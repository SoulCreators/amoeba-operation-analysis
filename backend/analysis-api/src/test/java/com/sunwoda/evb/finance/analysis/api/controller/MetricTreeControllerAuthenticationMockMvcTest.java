package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc coverage for the metric tree endpoint's authentication boundary. */
class MetricTreeControllerAuthenticationMockMvcTest {
    private static final String BATCH_NO = "BATCH-001";

    private MetricTreeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MetricTreeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricTreeController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsPrincipalWithNullNameWithUnauthorizedA0002AndSkipsService() throws Exception {
        // 认证主体对象存在但 Principal.getName() 为 null 时，指标树请求仍须返回 401/A0002，且不得进入 Service。
        mockMvc.perform(get("/api/v1/metric-tree/{batchNo}", BATCH_NO).principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户，拒绝返回指标树"));

        verifyNoInteractions(service);
    }
}
