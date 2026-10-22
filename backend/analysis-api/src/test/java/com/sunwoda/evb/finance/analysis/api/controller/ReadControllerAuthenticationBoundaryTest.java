package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.MoveBatchRequest;
import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import com.sunwoda.evb.finance.analysis.application.service.OverviewService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReadControllerAuthenticationBoundaryTest {
    private BatchService batchService;
    private OverviewService overviewService;
    private DatasetCatalog datasetCatalog;
    private MappingService mappingService;
    private MetricService metricService;

    private BatchController batchController;
    private OverviewController overviewController;
    private DatasetController datasetController;
    private MappingController mappingController;
    private MetricController metricController;

    @BeforeEach
    void setUp() {
        batchService = mock(BatchService.class);
        overviewService = mock(OverviewService.class);
        datasetCatalog = mock(DatasetCatalog.class);
        mappingService = mock(MappingService.class);
        metricService = mock(MetricService.class);

        batchController = new BatchController(batchService, mock(PermissionService.class));
        overviewController = new OverviewController(overviewService);
        datasetController = new DatasetController(datasetCatalog);
        mappingController = new MappingController(mappingService);
        metricController = new MetricController(metricService);
    }

    @Test
    void rejectsBatchGetAndMoveWhenPrincipalIsMissingOrUsernameIsBlank() {
        MoveBatchRequest request = new MoveBatchRequest();
        request.setStatus("READY_FOR_CALCULATION");

        assertRejected(principal -> batchController.get("BATCH-001", principal));
        assertRejected(principal -> batchController.move("BATCH-001", request, principal));

        verifyNoInteractions(batchService);
    }

    @Test
    void rejectsOverviewQueryWhenPrincipalIsMissingOrUsernameIsBlank() {
        assertRejected(principal -> overviewController.query("2026-01", "BASE", "ALL", principal));

        verifyNoInteractions(overviewService);
    }

    @Test
    void rejectsDatasetListWhenPrincipalIsMissingOrUsernameIsBlank() {
        assertRejected(principal -> datasetController.list("BASE", principal));

        verifyNoInteractions(datasetCatalog);
    }

    @Test
    void rejectsMappingListWhenPrincipalIsMissingOrUsernameIsBlank() {
        assertRejected(principal -> mappingController.list("SALES_PROJECT", false, principal));

        verifyNoInteractions(mappingService);
    }

    @Test
    void rejectsMetricListWhenPrincipalIsMissingOrUsernameIsBlank() {
        assertRejected(principal -> metricController.list("BASE", false, principal));

        verifyNoInteractions(metricService);
    }

    private static void assertRejected(java.util.function.Consumer<Principal> invocation) {
        assertRejected(() -> invocation.accept(null));
        assertRejected(() -> invocation.accept(() -> null));
        assertRejected(() -> invocation.accept(() -> ""));
        assertRejected(() -> invocation.accept(() -> "   "));
    }

    private static void assertRejected(Executable invocation) {
        assertThrows(IllegalStateException.class, invocation);
    }
}
