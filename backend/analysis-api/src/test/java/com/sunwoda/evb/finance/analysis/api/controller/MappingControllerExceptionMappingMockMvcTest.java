package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
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

/** HTTP/error-code mapping coverage for the mapping controller's existing GET contract. */
class MappingControllerExceptionMappingMockMvcTest {
    private static final String MAPPING_PATH = "/api/v1/mappings";
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
    void rejectsAnonymousMappingQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(mappingRequest(null).param("type", "SALES_PROJECT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankMappingQueryWithUnauthorizedA0002() throws Exception {
        mockMvc.perform(mappingRequest(principal("   ")).param("type", "SALES_PROJECT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalTypeToOkAndA0001() throws Exception {
        mockMvc.perform(mappingRequest(principal(USER_ID)).param("type", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromMappingServiceToOkAndA0001() throws Exception {
        when(service.list(MappingType.SALES_PROJECT, false))
                .thenThrow(new IllegalArgumentException("映射查询参数无效"));

        mockMvc.perform(mappingRequest(principal(USER_ID)).param("type", "SALES_PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("映射查询参数无效"));

        verify(service).list(MappingType.SALES_PROJECT, false);
    }

    @Test
    void mapsUnexpectedMappingServiceFailureToOkAndA9999() throws Exception {
        when(service.list(MappingType.SALES_PROJECT, false))
                .thenThrow(new RuntimeException("unexpected mapping failure"));

        mockMvc.perform(mappingRequest(principal(USER_ID)).param("type", "SALES_PROJECT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).list(MappingType.SALES_PROJECT, false);
    }

    @Test
    void rejectsMissingTypeAtRequestBindingBoundary() throws Exception {
        mockMvc.perform(mappingRequest(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder mappingRequest(Principal principal) {
        MockHttpServletRequestBuilder request = get(MAPPING_PATH);
        return principal == null ? request : request.principal(principal);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
