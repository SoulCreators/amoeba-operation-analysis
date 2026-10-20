package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.OverviewResultProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本次修改：为 OverviewServiceImpl 增加 provider 参数透传、scope 归一化及结果原样返回的纯单元契约测试。
 */
class OverviewServiceImplForwardingContractTest {

    /**
     * 本次修改：验证正常 query 将 period、AnalysisPerspective、非空 scope 原样传给 provider，且返回同一结果对象。
     */
    @Test
    void forwardsNormalQueryArgumentsAndReturnsProviderResultUnchanged() {
        OverviewResultProvider provider = mock(OverviewResultProvider.class);
        OverviewServiceImpl service = new OverviewServiceImpl(provider);
        String period = "2026-02";
        AnalysisPerspective perspective = AnalysisPerspective.PRODUCT_LINE;
        String scope = "SCOPE-001";
        OverviewMetricResult metric = metricResult();
        List<OverviewMetricResult> providerResult = Collections.singletonList(metric);
        when(provider.query(period, perspective, scope)).thenReturn(providerResult);

        List<OverviewMetricResult> actual = service.query(period, perspective, scope);

        verify(provider).query(period, perspective, scope);
        assertSame(providerResult, actual);
        assertSame(metric, actual.get(0));
    }

    /**
     * 本次修改：验证 null、空字符串和全空白 scope 均统一以 ALL 传给 provider。
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void normalizesMissingScopeToAll(String scope) {
        OverviewResultProvider provider = mock(OverviewResultProvider.class);
        OverviewServiceImpl service = new OverviewServiceImpl(provider);
        String period = "2026-02";
        AnalysisPerspective perspective = AnalysisPerspective.BASE;
        List<OverviewMetricResult> providerResult = Collections.singletonList(metricResult());
        when(provider.query(period, perspective, "ALL")).thenReturn(providerResult);

        List<OverviewMetricResult> actual = service.query(period, perspective, scope);

        verify(provider).query(period, perspective, "ALL");
        assertSame(providerResult, actual);
    }

    /**
     * 本次修改：单独确认 provider 返回的集合及其中的结果对象均不被 OverviewServiceImpl 改写或替换。
     */
    @Test
    void doesNotRewriteProviderResult() {
        OverviewResultProvider provider = mock(OverviewResultProvider.class);
        OverviewServiceImpl service = new OverviewServiceImpl(provider);
        OverviewMetricResult metric = metricResult();
        List<OverviewMetricResult> providerResult = Collections.singletonList(metric);
        when(provider.query("2026-02", AnalysisPerspective.BASE, "ALL")).thenReturn(providerResult);

        List<OverviewMetricResult> actual = service.query("2026-02", AnalysisPerspective.BASE, null);

        assertSame(providerResult, actual);
        assertSame(metric, actual.get(0));
    }

    private static OverviewMetricResult metricResult() {
        return new OverviewMetricResult("REVENUE", "营业收入", "CNY",
                new BigDecimal("100"), new BigDecimal("90"),
                new BigDecimal("10"), new BigDecimal("80"), "摘要");
    }
}
