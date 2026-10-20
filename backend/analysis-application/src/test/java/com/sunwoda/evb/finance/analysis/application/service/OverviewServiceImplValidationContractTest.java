package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.OverviewResultProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * OVERVIEW-SVC-001-B：验证 OverviewServiceImpl 当前参数校验契约，保持生产实现只读。
 */
class OverviewServiceImplValidationContractTest {
    private OverviewResultProvider provider;
    private OverviewServiceImpl service;

    @BeforeEach
    void setUp() {
        provider = mock(OverviewResultProvider.class);
        service = new OverviewServiceImpl(provider);
    }

    /** 验证 period 为 null 时保留既有异常类型和中文消息，并且 provider 不发生交互。 */
    @Test
    void rejectsNullPeriodWithoutProviderInteraction() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.query(null, AnalysisPerspective.BASE, "NANCHANG"));

        assertEquals("period和perspective不能为空", error.getMessage());
        verifyNoInteractions(provider);
    }

    /** 验证 AnalysisPerspective 为 null 时保留既有异常类型和中文消息，并且 provider 不发生交互。 */
    @Test
    void rejectsNullPerspectiveWithoutProviderInteraction() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.query("2026-09", null, "NANCHANG"));

        assertEquals("period和perspective不能为空", error.getMessage());
        verifyNoInteractions(provider);
    }
}
