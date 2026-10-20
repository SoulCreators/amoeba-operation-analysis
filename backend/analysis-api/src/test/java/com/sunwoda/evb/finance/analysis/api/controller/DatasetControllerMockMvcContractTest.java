package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetFieldDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DatasetControllerMockMvcContractTest {
    private static final String DATASET_PATH = "/api/v1/datasets";
    private static final String USER_ID = "U001";

    private DatasetCatalog datasetCatalog;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        datasetCatalog = mock(DatasetCatalog.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DatasetController(datasetCatalog))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedListReturnsSuccessfulEnvelopeAndAllDatasetFields() throws Exception {
        DatasetDefinition definition = new DatasetDefinition(
                "PLBU_ACT_INC_COST",
                "产品线/事业部实际收入成本",
                "MONTHLY",
                true,
                Arrays.asList(AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT),
                Arrays.asList(
                        new DatasetFieldDefinition("期间", "期间", "MONTH", true),
                        new DatasetFieldDefinition("收入", "销售收入", "DECIMAL", false)));
        when(datasetCatalog.list(AnalysisPerspective.PRODUCT_LINE))
                .thenReturn(Collections.singletonList(definition));

        mockMvc.perform(get(DATASET_PATH)
                        .param("perspective", "product_line")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("PLBU_ACT_INC_COST"))
                .andExpect(jsonPath("$.data[0].name").value("产品线/事业部实际收入成本"))
                .andExpect(jsonPath("$.data[0].category").value("MONTHLY"))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].perspectives").isArray())
                .andExpect(jsonPath("$.data[0].perspectives.length()").value(2))
                .andExpect(jsonPath("$.data[0].perspectives[0]").value("PRODUCT_LINE"))
                .andExpect(jsonPath("$.data[0].perspectives[1]").value("BUSINESS_UNIT"))
                .andExpect(jsonPath("$.data[0].fields").isArray())
                .andExpect(jsonPath("$.data[0].fields.length()").value(2))
                .andExpect(jsonPath("$.data[0].fields[0].fieldCode").value("期间"))
                .andExpect(jsonPath("$.data[0].fields[0].fieldName").value("期间"))
                .andExpect(jsonPath("$.data[0].fields[0].dataType").value("MONTH"))
                .andExpect(jsonPath("$.data[0].fields[0].required").value(true))
                .andExpect(jsonPath("$.data[0].fields[1].fieldCode").value("收入"))
                .andExpect(jsonPath("$.data[0].fields[1].fieldName").value("销售收入"))
                .andExpect(jsonPath("$.data[0].fields[1].dataType").value("DECIMAL"))
                .andExpect(jsonPath("$.data[0].fields[1].required").value(false));

        verify(datasetCatalog).list(AnalysisPerspective.PRODUCT_LINE);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
