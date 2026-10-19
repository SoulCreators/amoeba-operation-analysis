package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.AiAnalysisService;
import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.application.service.DataQualityService;
import com.sunwoda.evb.finance.analysis.application.service.MetricTreeService;
import com.sunwoda.evb.finance.analysis.application.service.PvmService;
import com.sunwoda.evb.finance.analysis.application.service.V13AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalysisControllerAuthenticationBoundaryTest {
    private BatchReadinessService batchReadinessService;
    private DataQualityService dataQualityService;
    private MetricTreeService metricTreeService;
    private PvmService pvmService;
    private AiAnalysisService aiAnalysisService;
    private V13AnalysisService v13AnalysisService;

    private BatchReadinessController batchReadinessController;
    private DataQualityController dataQualityController;
    private MetricTreeController metricTreeController;
    private PvmController pvmController;
    private AiAnalysisController aiAnalysisController;
    private V13AnalysisController v13AnalysisController;

    @BeforeEach
    void setUp() {
        batchReadinessService = mock(BatchReadinessService.class);
        dataQualityService = mock(DataQualityService.class);
        metricTreeService = mock(MetricTreeService.class);
        pvmService = mock(PvmService.class);
        aiAnalysisService = mock(AiAnalysisService.class);
        v13AnalysisService = mock(V13AnalysisService.class);

        batchReadinessController = new BatchReadinessController(batchReadinessService);
        dataQualityController = new DataQualityController(dataQualityService);
        metricTreeController = new MetricTreeController(metricTreeService);
        pvmController = new PvmController(pvmService);
        aiAnalysisController = new AiAnalysisController(aiAnalysisService);
        v13AnalysisController = new V13AnalysisController(v13AnalysisService);
    }

    @Test
    void rejectsBatchReadinessWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> batchReadinessController.get("BATCH-001", null));
        assertRejected(() -> batchReadinessController.get("BATCH-001", principalWithName("")));
        assertRejected(() -> batchReadinessController.get("BATCH-001", principalWithName("   ")));

        verifyNoInteractions(batchReadinessService);
    }

    @Test
    void rejectsDataQualityWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> dataQualityController.get("BATCH-001", null));
        assertRejected(() -> dataQualityController.get("BATCH-001", principalWithName("")));
        assertRejected(() -> dataQualityController.get("BATCH-001", principalWithName("   ")));

        verifyNoInteractions(dataQualityService);
    }

    @Test
    void rejectsMetricTreeWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> metricTreeController.calculate("BATCH-001", null));
        assertRejected(() -> metricTreeController.calculate("BATCH-001", principalWithName("")));
        assertRejected(() -> metricTreeController.calculate("BATCH-001", principalWithName("   ")));

        verifyNoInteractions(metricTreeService);
    }

    @Test
    void rejectsPvmWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> pvmController.analyze("BATCH-001", null));
        assertRejected(() -> pvmController.analyze("BATCH-001", principalWithName("")));
        assertRejected(() -> pvmController.analyze("BATCH-001", principalWithName("   ")));

        verifyNoInteractions(pvmService);
    }

    @Test
    void rejectsAiAnalysisWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> aiAnalysisController.generate("BATCH-001", null));
        assertRejected(() -> aiAnalysisController.generate("BATCH-001", principalWithName("")));
        assertRejected(() -> aiAnalysisController.generate("BATCH-001", principalWithName("   ")));
        assertRejected(() -> aiAnalysisController.get("BATCH-001", null));
        assertRejected(() -> aiAnalysisController.get("BATCH-001", principalWithName("")));
        assertRejected(() -> aiAnalysisController.get("BATCH-001", principalWithName("   ")));
        assertRejected(() -> aiAnalysisController.publish("BATCH-001", null));
        assertRejected(() -> aiAnalysisController.publish("BATCH-001", principalWithName("")));
        assertRejected(() -> aiAnalysisController.publish("BATCH-001", principalWithName("   ")));

        verifyNoInteractions(aiAnalysisService);
    }

    @Test
    void rejectsV13AnalysisWhenPrincipalIsMissingOrUsernameIsEmpty() {
        assertRejected(() -> v13AnalysisController.overview("BATCH-001", "ACT_BGT", null));
        assertRejected(() -> v13AnalysisController.overview("BATCH-001", "ACT_BGT", principalWithName("")));
        assertRejected(() -> v13AnalysisController.overview("BATCH-001", "ACT_BGT", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.matrix("BATCH-001", "GROSS_PROFIT", null));
        assertRejected(() -> v13AnalysisController.matrix("BATCH-001", "GROSS_PROFIT", principalWithName("")));
        assertRejected(() -> v13AnalysisController.matrix("BATCH-001", "GROSS_PROFIT", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.unitEfficiency("BATCH-001", null));
        assertRejected(() -> v13AnalysisController.unitEfficiency("BATCH-001", principalWithName("")));
        assertRejected(() -> v13AnalysisController.unitEfficiency("BATCH-001", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.forecast("BATCH-001", "ACT_BGT", "FC-001", null));
        assertRejected(() -> v13AnalysisController.forecast("BATCH-001", "ACT_BGT", "FC-001", principalWithName("")));
        assertRejected(() -> v13AnalysisController.forecast("BATCH-001", "ACT_BGT", "FC-001", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.risk("BATCH-001", null));
        assertRejected(() -> v13AnalysisController.risk("BATCH-001", principalWithName("")));
        assertRejected(() -> v13AnalysisController.risk("BATCH-001", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.waterfall("BATCH-001", null));
        assertRejected(() -> v13AnalysisController.waterfall("BATCH-001", principalWithName("")));
        assertRejected(() -> v13AnalysisController.waterfall("BATCH-001", principalWithName("   ")));
        assertRejected(() -> v13AnalysisController.topic("BATCH-001", "TOPIC-001", null));
        assertRejected(() -> v13AnalysisController.topic("BATCH-001", "TOPIC-001", principalWithName("")));
        assertRejected(() -> v13AnalysisController.topic("BATCH-001", "TOPIC-001", principalWithName("   ")));

        verifyNoInteractions(v13AnalysisService);
    }

    private static void assertRejected(Executable executable) {
        assertThrows(IllegalStateException.class, executable);
    }

    private static Principal principalWithName(String name) {
        Principal principal = mock(Principal.class);
        org.mockito.Mockito.when(principal.getName()).thenReturn(name);
        return principal;
    }
}
