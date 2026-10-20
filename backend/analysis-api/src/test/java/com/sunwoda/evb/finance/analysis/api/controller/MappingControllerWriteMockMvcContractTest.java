package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the mapping write APIs. */
class MappingControllerWriteMockMvcContractTest {
    private static final String MAPPINGS_PATH = "/api/v1/mappings";
    private static final String USER_ID = "U001";

    private MappingService mappingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mappingService = mock(MappingService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MappingController(mappingService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedUpsertPassesRequestFieldsAndPrincipalToService() throws Exception {
        when(mappingService.upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", false, USER_ID))
                .thenReturn(mapping(101L, MappingType.SALES_PROJECT, "项目A", "产品线一", false, USER_ID));

        mockMvc.perform(post(MAPPINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SALES_PROJECT\",\"matchKey\":\"项目A\","
                                + "\"mappedValue\":\"产品线一\",\"enabled\":false,"
                                + "\"updatedBy\":\"request-user\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.type").value("SALES_PROJECT"))
                .andExpect(jsonPath("$.data.matchKey").value("项目A"))
                .andExpect(jsonPath("$.data.mappedValue").value("产品线一"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.updatedBy").value(USER_ID))
                .andExpect(jsonPath("$.data.updatedAt").isArray());

        verify(mappingService).upsert(MappingType.SALES_PROJECT, "项目A", "产品线一", false, USER_ID);
    }

    @Test
    void authenticatedUpsertDefaultsEnabledToTrueWhenFieldIsMissing() throws Exception {
        when(mappingService.upsert(MappingType.EXPENSE_RULE, "费用A", "管理费用", true, USER_ID))
                .thenReturn(mapping(102L, MappingType.EXPENSE_RULE, "费用A", "管理费用", true, USER_ID));

        mockMvc.perform(post(MAPPINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE_RULE\",\"matchKey\":\"费用A\","
                                + "\"mappedValue\":\"管理费用\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(mappingService).upsert(MappingType.EXPENSE_RULE, "费用A", "管理费用", true, USER_ID);
    }

    @Test
    void authenticatedDisablePassesTypeMatchKeyAndPrincipalAndReturnsServiceResult() throws Exception {
        when(mappingService.disable(MappingType.INVENTORY, "SKU-001", USER_ID))
                .thenReturn(mapping(103L, MappingType.INVENTORY, "SKU-001", "原材料", false, USER_ID));

        mockMvc.perform(post(MAPPINGS_PATH + "/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INVENTORY\",\"matchKey\":\"SKU-001\","
                                + "\"mappedValue\":\"请求值不作为停用结果\",\"enabled\":true,"
                                + "\"updatedBy\":\"request-user\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").value(103))
                .andExpect(jsonPath("$.data.type").value("INVENTORY"))
                .andExpect(jsonPath("$.data.matchKey").value("SKU-001"))
                .andExpect(jsonPath("$.data.mappedValue").value("原材料"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.updatedBy").value(USER_ID))
                .andExpect(jsonPath("$.data.updatedAt").isArray());

        verify(mappingService).disable(MappingType.INVENTORY, "SKU-001", USER_ID);
    }

    private MappingEntry mapping(Long id, MappingType type, String matchKey, String mappedValue,
                                 boolean enabled, String updatedBy) {
        return new MappingEntry(id, type, matchKey, mappedValue, enabled, updatedBy,
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
