package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.V13AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V1.3 七类查询接口的 HTTP 现状契约测试。
 *
 * <p>本次只冻结路由、默认参数、Principal 透传和成功 envelope，不改变公式、权限能力或
 * PENDING/CALCULATED_TECHNICAL 等业务状态语义。</p>
 */
class V13AnalysisControllerAllQueriesMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private V13AnalysisService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(V13AnalysisService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V13AnalysisController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void overviewUsesExistingDefaultScenarioAndPassesUser() throws Exception {
        when(service.overview(BATCH_NO, USER_ID, "ACT_BGT")).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/overview/{batchNo}", BATCH_NO)
                        .principal(() -> " " + USER_ID + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).overview(BATCH_NO, USER_ID, "ACT_BGT");
        verifyNoMoreInteractions(service);
    }

    @Test
    void matrixUsesExistingDefaultMetricAndPassesUser() throws Exception {
        when(service.matrix(BATCH_NO, USER_ID, "GROSS_PROFIT")).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/matrix/{batchNo}", BATCH_NO)
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).matrix(BATCH_NO, USER_ID, "GROSS_PROFIT");
        verifyNoMoreInteractions(service);
    }

    @Test
    void unitEfficiencyPassesUserAndPreservesSuccessEnvelope() throws Exception {
        when(service.unitEfficiency(BATCH_NO, USER_ID)).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/unit-efficiency/{batchNo}", BATCH_NO)
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).unitEfficiency(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void forecastUsesExistingDefaultScenarioAndAllowsMissingFcVersion() throws Exception {
        when(service.forecast(BATCH_NO, USER_ID, "ACT_BGT", null)).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/forecast/{batchNo}", BATCH_NO)
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).forecast(BATCH_NO, USER_ID, "ACT_BGT", null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void riskPassesUserAndPreservesSuccessEnvelope() throws Exception {
        when(service.risk(BATCH_NO, USER_ID)).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/risk/{batchNo}", BATCH_NO)
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).risk(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void waterfallPassesUserAndPreservesSuccessEnvelope() throws Exception {
        when(service.waterfall(BATCH_NO, USER_ID)).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/waterfall/{batchNo}", BATCH_NO)
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).waterfall(BATCH_NO, USER_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void topicPassesTopicCodeAndUserAndPreservesSuccessEnvelope() throws Exception {
        when(service.topic(BATCH_NO, USER_ID, "INVENTORY")).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/topics/{batchNo}/{topicCode}", BATCH_NO, "INVENTORY")
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO));

        verify(service).topic(BATCH_NO, USER_ID, "INVENTORY");
        verifyNoMoreInteractions(service);
    }

    @Test
    void explicitQueryParametersReachTheCorrespondingServiceMethods() throws Exception {
        when(service.overview(BATCH_NO, USER_ID, "ACT_FC")).thenReturn(data());
        when(service.matrix(BATCH_NO, USER_ID, "NET_PROFIT")).thenReturn(data());
        when(service.forecast(BATCH_NO, USER_ID, "ACT_FC", "FC-001")).thenReturn(data());

        mockMvc.perform(get("/api/v1/v13/overview/{batchNo}", BATCH_NO)
                        .param("scenario", "ACT_FC")
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/v1/v13/matrix/{batchNo}", BATCH_NO)
                        .param("metric", "NET_PROFIT")
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/v1/v13/forecast/{batchNo}", BATCH_NO)
                        .param("scenario", "ACT_FC")
                        .param("fcVersion", "FC-001")
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 显式参数只验证 Controller 透传，不在测试中定义任何财务公式或版本规则。
        verify(service).overview(BATCH_NO, USER_ID, "ACT_FC");
        verify(service).matrix(BATCH_NO, USER_ID, "NET_PROFIT");
        verify(service).forecast(BATCH_NO, USER_ID, "ACT_FC", "FC-001");
        verifyNoMoreInteractions(service);
    }

    @Test
    void everyQueryRouteRejectsMissingPrincipalBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/v13/overview/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/matrix/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/unit-efficiency/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/forecast/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/risk/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/waterfall/{batchNo}", BATCH_NO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/v13/topics/{batchNo}/{topicCode}", BATCH_NO, "INVENTORY"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 认证主体存在但用户名为 null 时，七类查询仍必须在认证门禁处返回 401。
        mockMvc.perform(get("/api/v1/v13/overview/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/matrix/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/unit-efficiency/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/forecast/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/risk/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/waterfall/{batchNo}", BATCH_NO)
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/v13/topics/{batchNo}/{topicCode}", BATCH_NO, "INVENTORY")
                        .principal(() -> null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                // Principal.getName() 为 null 仍由统一认证异常处理映射为 A0002。
                .andExpect(jsonPath("$.code").value("A0002"));

        // 七类查询均在 Controller 认证门禁处结束，不进入任何业务 Service。
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    /** 使用最小结果 map 验证统一成功包络，避免引入公式或演示数据。 */
    private Map<String, Object> data() {
        return Collections.<String, Object>singletonMap("batchNo", BATCH_NO);
    }
}
