package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.MetricTreeService;
import com.sunwoda.evb.finance.analysis.domain.model.MetricResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 指标树 Controller 当前 HTTP 包络契约测试。
 *
 * 本次修改只验证现有 Service 返回值、异常处理和 Principal 参数透传，
 * 不定义指标公式、展示规则、权限矩阵或新的错误码。
 */
class MetricTreeControllerMockMvcContractTest {
    private static final String METRIC_TREE_PATH = "/api/v1/metric-tree/{batchNo}";
    private static final String BATCH_NO = "BATCH-METRIC-001";
    private static final String USER_ID = "  user-001  ";

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
    void returnsSuccessfulEnvelopeAndMetricFieldsAndPassesPrincipalNameThrough() throws Exception {
        // 本次新增成功列表契约，验证批次号和带首尾空格的 Principal 用户名按当前实现透传给 Service。
        MetricResult metric = new MetricResult(
                "SCOPE-001",
                "REVENUE",
                "收入",
                "元",
                new BigDecimal("120.50"),
                new BigDecimal("100.00"),
                new BigDecimal("20.50"),
                "收入高于预算");
        when(service.calculate(BATCH_NO, USER_ID)).thenReturn(Collections.singletonList(metric));

        mockMvc.perform(get(METRIC_TREE_PATH, BATCH_NO).principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].scopeCode").value("SCOPE-001"))
                .andExpect(jsonPath("$.data[0].metricCode").value("REVENUE"))
                .andExpect(jsonPath("$.data[0].metricName").value("收入"))
                .andExpect(jsonPath("$.data[0].unit").value("元"))
                .andExpect(jsonPath("$.data[0].actualValue").value(120.50))
                .andExpect(jsonPath("$.data[0].budgetValue").value(100.00))
                .andExpect(jsonPath("$.data[0].gapValue").value(20.50))
                .andExpect(jsonPath("$.data[0].aiSummary").value("收入高于预算"));

        verify(service).calculate(BATCH_NO, USER_ID);
    }

    @Test
    void returnsSuccessfulEnvelopeWithEmptyList() throws Exception {
        // 本次新增空列表契约，确认 Service 返回空集合时仍保持成功包络和数组类型。
        when(service.calculate(BATCH_NO, "user-002")).thenReturn(Collections.emptyList());

        mockMvc.perform(get(METRIC_TREE_PATH, BATCH_NO).principal(principal("user-002")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(service).calculate(BATCH_NO, "user-002");
    }

    @Test
    void mapsServiceIllegalArgumentExceptionToCurrentA0001Envelope() throws Exception {
        // 本次新增 Service 参数异常契约，沿用现有 ApiExceptionHandler 的 A0001 和 HTTP 200 语义。
        when(service.calculate(BATCH_NO, "user-003"))
                .thenThrow(new IllegalArgumentException("指标树批次参数无效"));

        mockMvc.perform(get(METRIC_TREE_PATH, BATCH_NO).principal(principal("user-003")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("指标树批次参数无效"));

        verify(service).calculate(BATCH_NO, "user-003");
    }

    @Test
    void mapsUnexpectedServiceExceptionToCurrentA9999Envelope() throws Exception {
        // 本次新增 Service 未知异常契约，沿用现有 ApiExceptionHandler 的 A9999 和通用错误文案。
        when(service.calculate(BATCH_NO, "user-004"))
                .thenThrow(new RuntimeException("unexpected metric tree failure"));

        mockMvc.perform(get(METRIC_TREE_PATH, BATCH_NO).principal(principal("user-004")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).calculate(BATCH_NO, "user-004");
    }

    @Test
    void trimsPrincipalNameOnlyForBlankValidationAndRejectsBlankName() throws Exception {
        // 本次新增 Principal trim 边界契约，确认全空白用户名被拒绝且不进入 Service。
        mockMvc.perform(get(METRIC_TREE_PATH, BATCH_NO).principal(principal("   ")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户，拒绝返回指标树"));

        verifyNoInteractions(service);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
