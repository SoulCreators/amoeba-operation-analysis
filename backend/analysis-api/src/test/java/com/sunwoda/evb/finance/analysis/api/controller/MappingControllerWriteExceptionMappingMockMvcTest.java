package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP/error-code mapping coverage for the mapping write APIs. */
class MappingControllerWriteExceptionMappingMockMvcTest {
    private static final String MAPPINGS_PATH = "/api/v1/mappings";
    private static final String USER_ID = "U001";

    private MappingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MappingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MappingController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsAnonymousUpsertWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(upsertRequest(null, "{\"type\":\"SALES_PROJECT\","
                        + "\"matchKey\":\"项目A\",\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankDisableWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(disableRequest(principal("   "), "{\"type\":\"INVENTORY\","
                        + "\"matchKey\":\"SKU-001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalUpsertTypeToOkAndA0001WithoutCallingService() throws Exception {
        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"type\":\"UNKNOWN\","
                        + "\"matchKey\":\"项目A\",\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromUpsertServiceToOkAndA0001() throws Exception {
        when(service.upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", true, USER_ID))
                .thenThrow(new IllegalArgumentException("映射值不能为空"));

        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"type\":\"SALES_PROJECT\","
                        + "\"matchKey\":\"项目A\",\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("映射值不能为空"));

        verify(service).upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", true, USER_ID);
    }

    @Test
    void mapsIllegalArgumentFromDisableServiceToOkAndA0001() throws Exception {
        when(service.disable(MappingType.INVENTORY, "SKU-001", USER_ID))
                .thenThrow(new IllegalArgumentException("映射不存在: SKU-001"));

        mockMvc.perform(disableRequest(principal(USER_ID), "{\"type\":\"INVENTORY\","
                        + "\"matchKey\":\"SKU-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("映射不存在: SKU-001"));

        verify(service).disable(MappingType.INVENTORY, "SKU-001", USER_ID);
    }

    @Test
    void mapsUnexpectedUpsertServiceFailureToOkAndA9999() throws Exception {
        when(service.upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", true, USER_ID))
                .thenThrow(new RuntimeException("unexpected mapping write failure"));

        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"type\":\"SALES_PROJECT\","
                        + "\"matchKey\":\"项目A\",\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", true, USER_ID);
    }

    @Test
    void missingUpsertTypeCurrentlyMapsToOkAndA9999BeforeServiceCall() throws Exception {
        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"matchKey\":\"项目A\","
                        + "\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void missingUpsertMatchKeyCurrentlyPassesNullToServiceAndReturnsSuccessWithNullData() throws Exception {
        when(service.upsert(MappingType.SALES_PROJECT, null, "产品线一", true, USER_ID))
                .thenReturn(null);

        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"type\":\"SALES_PROJECT\","
                        + "\"mappedValue\":\"产品线一\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).upsert(MappingType.SALES_PROJECT, null, "产品线一", true, USER_ID);
    }

    @Test
    void missingUpsertMappedValueCurrentlyPassesNullToServiceAndReturnsSuccessWithNullData() throws Exception {
        when(service.upsert(MappingType.SALES_PROJECT, "项目A", null, true, USER_ID))
                .thenReturn(null);

        mockMvc.perform(upsertRequest(principal(USER_ID), "{\"type\":\"SALES_PROJECT\","
                        + "\"matchKey\":\"项目A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("OK"));

        verify(service).upsert(MappingType.SALES_PROJECT, "项目A", null, true, USER_ID);
    }

    private MockHttpServletRequestBuilder upsertRequest(Principal principal, String body) {
        MockHttpServletRequestBuilder request = post(MAPPINGS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return principal == null ? request : request.principal(principal);
    }

    private MockHttpServletRequestBuilder disableRequest(Principal principal, String body) {
        MockHttpServletRequestBuilder request = post(MAPPINGS_PATH + "/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
