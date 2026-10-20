package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for the mapping catalogue read API. */
class MappingControllerMockMvcContractTest {
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
    void authenticatedQueryConvertsTypeAndDefaultsIncludeDisabledToFalse() throws Exception {
        when(mappingService.list(MappingType.SALES_PROJECT, false))
                .thenReturn(Collections.singletonList(mapping(MappingType.SALES_PROJECT)));

        mockMvc.perform(get(MAPPINGS_PATH)
                        .param("type", "SALES_PROJECT")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(mappingService).list(MappingType.SALES_PROJECT, false);
    }

    @Test
    void explicitIncludeDisabledTrueIsPassedToService() throws Exception {
        when(mappingService.list(MappingType.INVENTORY, true))
                .thenReturn(Collections.singletonList(mapping(MappingType.INVENTORY)));

        mockMvc.perform(get(MAPPINGS_PATH)
                        .param("type", "INVENTORY")
                        .param("includeDisabled", "true")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("INVENTORY"));

        verify(mappingService).list(MappingType.INVENTORY, true);
    }

    @Test
    void successfulResponseSerializesEveryMappingEntryField() throws Exception {
        when(mappingService.list(MappingType.FX_RATE, false))
                .thenReturn(Collections.singletonList(mapping(MappingType.FX_RATE)));

        mockMvc.perform(get(MAPPINGS_PATH)
                        .param("type", "FX_RATE")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].type").value("FX_RATE"))
                .andExpect(jsonPath("$.data[0].matchKey").value("项目A"))
                .andExpect(jsonPath("$.data[0].mappedValue").value("产品线一"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].updatedBy").value(USER_ID))
                .andExpect(jsonPath("$.data[0].updatedAt").isArray())
                .andExpect(jsonPath("$.data[0].updatedAt[0]").value(2026))
                .andExpect(jsonPath("$.data[0].updatedAt[1]").value(10))
                .andExpect(jsonPath("$.data[0].updatedAt[2]").value(20))
                .andExpect(jsonPath("$.data[0].updatedAt[3]").value(10))
                .andExpect(jsonPath("$.data[0].updatedAt[4]").value(15))
                .andExpect(jsonPath("$.data[0].updatedAt[5]").value(30));

        verify(mappingService).list(MappingType.FX_RATE, false);
    }

    private MappingEntry mapping(MappingType type) {
        return new MappingEntry(
                101L,
                type,
                "项目A",
                "产品线一",
                true,
                USER_ID,
                LocalDateTime.of(2026, 10, 20, 10, 15, 30));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
