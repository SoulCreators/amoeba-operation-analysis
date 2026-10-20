package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the dataset controller's existing contracts. */
class DatasetControllerExceptionMappingMockMvcTest {
    private static final String DATASET_PATH = "/api/v1/datasets";
    private static final String USER_ID = "U001";

    private DatasetCatalog catalog;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalog = mock(DatasetCatalog.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DatasetController(catalog))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsAnonymousDatasetQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(datasetRequest(null, "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsBlankDatasetQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(datasetRequest(principal("   "), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(catalog);
    }

    @Test
    void rejectsNullNameDatasetQueryWithUnauthorizedA0002() throws Exception {
        // 认证主体存在但 Principal.getName() 为 null 时，仍须在进入数据集目录前拒绝请求。
        mockMvc.perform(datasetRequest(principal(null), "BASE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(catalog);
    }

    @Test
    void mapsIllegalPerspectiveToOkAndA0001() throws Exception {
        mockMvc.perform(datasetRequest(principal(USER_ID), "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的数据视角: UNKNOWN"));

        verifyNoInteractions(catalog);
    }

    @Test
    void mapsIllegalArgumentFromCatalogToOkAndA0001() throws Exception {
        when(catalog.list(AnalysisPerspective.BASE))
                .thenThrow(new IllegalArgumentException("数据集目录参数无效"));

        mockMvc.perform(datasetRequest(principal(USER_ID), "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("数据集目录参数无效"));

        verify(catalog).list(AnalysisPerspective.BASE);
    }

    @Test
    void mapsUnexpectedCatalogFailureToOkAndA9999() throws Exception {
        when(catalog.list(AnalysisPerspective.BASE))
                .thenThrow(new RuntimeException("unexpected dataset catalog failure"));

        mockMvc.perform(datasetRequest(principal(USER_ID), "BASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(catalog).list(AnalysisPerspective.BASE);
    }

    private MockHttpServletRequestBuilder datasetRequest(Principal principal, String perspective) {
        MockHttpServletRequestBuilder request = get(DATASET_PATH)
                .param("perspective", perspective);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
