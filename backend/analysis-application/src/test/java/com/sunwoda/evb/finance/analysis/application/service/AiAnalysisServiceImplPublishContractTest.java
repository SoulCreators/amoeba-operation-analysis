package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本次修改：补齐 AiAnalysisServiceImpl.publish 的发布链路契约测试，保持生产代码不变。
 * 测试只覆盖现有草稿、结果、AI 发布权限及草稿仓储交互，不新增角色或可见性口径。
 */
@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceImplPublishContractTest {
    private static final String BATCH_NO = "B-AI-PUBLISH";
    private static final String USER_ID = "U-PUBLISHER";
    private static final AnalysisPerspective PERSPECTIVE = AnalysisPerspective.BASE;

    @Mock
    private CalculationService calculationService;

    @Mock
    private AiAnalysisDraftRepository draftRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private LocalAiGateway gateway;

    @Mock
    private AnalysisBatchRepository batchRepository;

    @Mock
    private CalculationResult result;

    private AiAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisServiceImpl(calculationService, gateway, batchRepository,
                permissionService, draftRepository);
        when(result.getPerspective()).thenReturn(PERSPECTIVE);
    }

    /** 验证有 AI 发布权限时按既定顺序读取草稿和结果，并完成发布及一次保存。 */
    @Test
    void canPublishAi为真时发布草稿并按顺序保存() {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.DRAFT);
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);
        when(draftRepository.findByBatchNo(BATCH_NO)).thenReturn(Optional.of(draft));
        when(permissionService.canPublishAi(USER_ID, PERSPECTIVE)).thenReturn(true);
        when(draftRepository.save(any(AiAnalysisDraft.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AiAnalysisDraft published = service.publish(BATCH_NO, USER_ID);

        assertEquals(AiAnalysisDraftStatus.PUBLISHED, published.getStatus());
        assertEquals(USER_ID, published.getPublishedBy());
        assertNotNull(published.getPublishedAt());

        ArgumentCaptor<AiAnalysisDraft> savedCaptor =
                ArgumentCaptor.forClass(AiAnalysisDraft.class);
        InOrder order = inOrder(calculationService, draftRepository, permissionService);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(draftRepository).findByBatchNo(BATCH_NO);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(permissionService).canPublishAi(USER_ID, PERSPECTIVE);
        order.verify(draftRepository).save(savedCaptor.capture());
        assertSame(published, savedCaptor.getValue());
        verify(calculationService, times(2)).getForUser(BATCH_NO, USER_ID);
        verify(draftRepository, times(1)).findByBatchNo(BATCH_NO);
        verify(permissionService, times(1)).canPublishAi(USER_ID, PERSPECTIVE);
    }

    /** 验证没有 AI 发布权限时保留当前 IllegalStateException，并且不会保存草稿。 */
    @Test
    void canPublishAi为假时抛当前异常且不保存() {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.DRAFT);
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);
        when(draftRepository.findByBatchNo(BATCH_NO)).thenReturn(Optional.of(draft));
        when(permissionService.canPublishAi(USER_ID, PERSPECTIVE)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.publish(BATCH_NO, USER_ID));

        assertEquals("当前用户无AI分析发布权限", exception.getMessage());
        InOrder order = inOrder(calculationService, draftRepository, permissionService);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(draftRepository).findByBatchNo(BATCH_NO);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(permissionService).canPublishAi(USER_ID, PERSPECTIVE);
        verify(calculationService, times(2)).getForUser(BATCH_NO, USER_ID);
        verify(draftRepository, times(1)).findByBatchNo(BATCH_NO);
        verify(draftRepository, never()).save(any(AiAnalysisDraft.class));
    }

    /** 验证已发布草稿继续由 AiAnalysisDraft.publish 抛出当前异常，并且不保存。 */
    @Test
    void 已发布草稿沿用领域对象当前异常且不保存() {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.PUBLISHED);
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);
        when(draftRepository.findByBatchNo(BATCH_NO)).thenReturn(Optional.of(draft));
        when(permissionService.canPublishAi(USER_ID, PERSPECTIVE)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.publish(BATCH_NO, USER_ID));

        assertEquals("只有AI草稿可以发布: PUBLISHED", exception.getMessage());
        InOrder order = inOrder(calculationService, draftRepository, permissionService);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(draftRepository).findByBatchNo(BATCH_NO);
        order.verify(calculationService).getForUser(BATCH_NO, USER_ID);
        order.verify(permissionService).canPublishAi(USER_ID, PERSPECTIVE);
        verify(calculationService, times(2)).getForUser(BATCH_NO, USER_ID);
        verify(draftRepository, times(1)).findByBatchNo(BATCH_NO);
        verify(draftRepository, never()).save(any(AiAnalysisDraft.class));
    }

    private static AiAnalysisDraft draft(AiAnalysisDraftStatus status) {
        return new AiAnalysisDraft(BATCH_NO, "V-AI-001", Collections.singletonMap("summary", "内容"),
                "U-GENERATOR", LocalDateTime.now().minusMinutes(1), status,
                status == AiAnalysisDraftStatus.PUBLISHED ? "U-OLD-PUBLISHER" : null,
                status == AiAnalysisDraftStatus.PUBLISHED ? LocalDateTime.now().minusSeconds(1) : null);
    }
}
