package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.same;

/**
 * AI-SVC-001 生成链路当前技术行为契约测试。
 * 本次修改仅锁定 generate 的状态判断、依赖调用参数和草稿保存字段。
 */
@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceImplGenerateContractTest {
    private static final String BATCH_NO = "B-AI-001";
    private static final String USER_ID = "U-FIN-001";
    private static final String VERSION_NO = "V-2026-01";

    @Mock
    private CalculationService calculationService;

    @Mock
    private LocalAiGateway gateway;

    @Mock
    private AnalysisBatchRepository batchRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AiAnalysisDraftRepository draftRepository;

    @ParameterizedTest
    @EnumSource(value = CalculationResultStatus.class, names = {"CONFIRMED", "PUBLISHED"})
    void confirmedOrPublishedResultCallsGatewayAndSavesDraft(CalculationResultStatus status) {
        CalculationResult result = result(status);
        Map<String, String> sections = new LinkedHashMap<String, String>();
        sections.put("summary", "summary-content");
        sections.put("risk", "risk-content");
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);
        when(gateway.generate(same(result))).thenReturn(sections);
        doAnswer(invocation -> invocation.getArgument(0))
                .when(draftRepository).save(any(AiAnalysisDraft.class));

        AiAnalysisDraft draft = service().generate(BATCH_NO, USER_ID);

        ArgumentCaptor<AiAnalysisDraft> savedDraft =
                ArgumentCaptor.forClass(AiAnalysisDraft.class);
        verify(calculationService).getForUser(BATCH_NO, USER_ID);
        verify(gateway).generate(same(result));
        verify(draftRepository).save(savedDraft.capture());

        assertSame(savedDraft.getValue(), draft);
        assertEquals(BATCH_NO, savedDraft.getValue().getBatchNo());
        assertEquals(VERSION_NO, savedDraft.getValue().getVersionNo());
        assertEquals(USER_ID, savedDraft.getValue().getGeneratedBy());
        assertEquals(AiAnalysisDraftStatus.DRAFT, savedDraft.getValue().getStatus());
        assertEquals(sections, savedDraft.getValue().getSections());
        verifyNoInteractions(batchRepository, permissionService);
    }

    @Test
    void calculatedResultDoesNotCallGatewayOrSaveDraft() {
        CalculationResult result = result(CalculationResultStatus.CALCULATED);
        when(calculationService.getForUser(BATCH_NO, USER_ID)).thenReturn(result);

        assertThrows(IllegalStateException.class,
                () -> service().generate(BATCH_NO, USER_ID));

        verify(calculationService).getForUser(BATCH_NO, USER_ID);
        verifyNoInteractions(gateway, draftRepository, batchRepository, permissionService);
    }

    private AiAnalysisServiceImpl service() {
        return new AiAnalysisServiceImpl(calculationService, gateway, batchRepository,
                permissionService, draftRepository);
    }

    private static CalculationResult result(CalculationResultStatus status) {
        return new CalculationResult(BATCH_NO, AnalysisPerspective.BASE,
                java.util.Collections.emptyList(), VERSION_NO, status,
                null, null, LocalDateTime.now(), null, null);
    }
}
