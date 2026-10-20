package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本次修改：固化 AiAnalysisServiceImpl.get 的结果访问门禁与草稿读取契约。
 */
@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceImplGetContractTest {
    private static final String BATCH_NO = "B-AI-GET";
    private static final String USER_ID = "U-ANALYST";

    @Mock
    private CalculationService calculationService;

    @Mock
    private AiAnalysisDraftRepository draftRepository;

    @Mock
    private LocalAiGateway gateway;

    @Mock
    private AnalysisBatchRepository batchRepository;

    @Mock
    private PermissionService permissionService;

    private AiAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisServiceImpl(calculationService, gateway, batchRepository,
                permissionService, draftRepository);
    }

    @Test
    void 有结果访问权限时先校验结果再原样返回草稿() {
        CalculationResult result = org.mockito.Mockito.mock(CalculationResult.class);
        AiAnalysisDraft draft = org.mockito.Mockito.mock(AiAnalysisDraft.class);
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);
        when(draftRepository.findByBatchNo(BATCH_NO)).thenReturn(Optional.of(draft));

        AiAnalysisDraft actual = service.get(BATCH_NO, USER_ID);

        assertSame(draft, actual);
        InOrder order = inOrder(calculationService, draftRepository);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(draftRepository).findByBatchNo(BATCH_NO);
    }

    @Test
    void 结果可访问但无草稿时抛出当前异常() {
        when(calculationService.getForUser(BATCH_NO, USER_ID))
                .thenReturn(org.mockito.Mockito.mock(CalculationResult.class));
        when(draftRepository.findByBatchNo(BATCH_NO)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.get(BATCH_NO, USER_ID));

        org.junit.jupiter.api.Assertions.assertEquals("AI分析尚未生成: " + BATCH_NO,
                exception.getMessage());
        verify(draftRepository).findByBatchNo(BATCH_NO);
    }

    @Test
    void 结果访问抛异常时不读取草稿() {
        RuntimeException expected = new RuntimeException("结果访问失败");
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenThrow(expected);

        RuntimeException actual = assertThrows(RuntimeException.class,
                () -> service.get(BATCH_NO, USER_ID));

        assertSame(expected, actual);
        verify(draftRepository, never()).findByBatchNo(BATCH_NO);
    }
}
