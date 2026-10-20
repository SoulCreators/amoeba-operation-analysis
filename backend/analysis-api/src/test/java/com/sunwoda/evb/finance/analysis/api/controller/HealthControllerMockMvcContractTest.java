package com.sunwoda.evb.finance.analysis.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 健康检查接口的 HTTP 包络与身份无关性契约覆盖。 */
class HealthControllerMockMvcContractTest {
    private static final String HEALTH_PATH = "/api/v1/health";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController())
                .build();
    }

    @Test
    void anonymousRequestReturnsSuccessfulHealthEnvelope() throws Exception {
        // 本次新增匿名健康检查契约，锁定现有 ApiResult 成功包络和 UP 状态。
        mockMvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void principalDoesNotChangeHealthResponse() throws Exception {
        // 本次新增带身份主体的对照契约，证明健康检查不读取 Principal 且结果保持一致。
        MvcResult anonymous = mockMvc.perform(get(HEALTH_PATH))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult authenticated = mockMvc.perform(get(HEALTH_PATH)
                        .principal(() -> "任意用户"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andReturn();

        assertEquals(anonymous.getResponse().getContentAsString(),
                authenticated.getResponse().getContentAsString());
    }
}
