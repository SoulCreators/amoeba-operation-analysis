package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CalculateRequest;
import com.sunwoda.evb.finance.analysis.api.dto.CreateBatchRequest;
import com.sunwoda.evb.finance.analysis.api.dto.CreateConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.CreateFcVersionRequest;
import com.sunwoda.evb.finance.analysis.api.dto.CreateImportTaskRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ResultLifecycleRequest;
import com.sunwoda.evb.finance.analysis.api.dto.UpsertMappingRequest;
import com.sunwoda.evb.finance.analysis.api.dto.UpsertMetricRequest;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.application.service.FcVersionCommandService;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class IngestionControllerAuthenticationBoundaryTest {
    private BatchService batchService;
    private ImportTaskService importTaskService;
    private MappingService mappingService;
    private MetricService metricService;
    private ConfigurationImportService configurationImportService;
    private FcVersionCommandService fcVersionCommandService;
    private CalculationService calculationService;

    private BatchController batchController;
    private ImportTaskController importTaskController;
    private MappingController mappingController;
    private MetricController metricController;
    private ConfigurationImportController configurationImportController;
    private FcVersionController fcVersionController;
    private CalculationController calculationController;

    @BeforeEach
    void setUp() {
        batchService = mock(BatchService.class);
        importTaskService = mock(ImportTaskService.class);
        mappingService = mock(MappingService.class);
        metricService = mock(MetricService.class);
        configurationImportService = mock(ConfigurationImportService.class);
        fcVersionCommandService = mock(FcVersionCommandService.class);
        calculationService = mock(CalculationService.class);

        batchController = new BatchController(batchService, mock(PermissionService.class));
        importTaskController = new ImportTaskController(importTaskService);
        mappingController = new MappingController(mappingService);
        metricController = new MetricController(metricService);
        configurationImportController = new ConfigurationImportController(configurationImportService);
        fcVersionController = new FcVersionController(fcVersionCommandService);
        calculationController = new CalculationController(calculationService);
    }

    @Test
    void batchCreateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        CreateBatchRequest request = new CreateBatchRequest();
        request.setPerspective("BASE");

        assertRejectsForInvalidIdentity(principal -> batchController.create(request, principal), batchService);
    }

    @Test
    void importTaskCreateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        CreateImportTaskRequest request = new CreateImportTaskRequest();

        assertRejectsForInvalidIdentity(principal ->
                importTaskController.create("BATCH-001", request, principal), importTaskService);
    }

    @Test
    void mappingUpsertRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        UpsertMappingRequest request = new UpsertMappingRequest();
        request.setType("SALES_PROJECT");

        assertRejectsForInvalidIdentity(principal -> mappingController.upsert(request, principal), mappingService);
    }

    @Test
    void mappingDisableRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        UpsertMappingRequest request = new UpsertMappingRequest();
        request.setType("SALES_PROJECT");

        assertRejectsForInvalidIdentity(principal -> mappingController.disable(request, principal), mappingService);
    }

    @Test
    void metricUpsertRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        UpsertMetricRequest request = new UpsertMetricRequest();

        assertRejectsForInvalidIdentity(principal -> metricController.upsert(request, principal), metricService);
    }

    @Test
    void configurationImportCreateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        CreateConfigurationImportRequest request = new CreateConfigurationImportRequest();
        request.setType("FX_RATE");

        assertRejectsForInvalidIdentity(principal ->
                configurationImportController.create(request, principal), configurationImportService);
    }

    @Test
    void configurationImportUploadRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        assertRejectsForInvalidIdentity(principal ->
                configurationImportController.upload("TASK-001", null, principal), configurationImportService);
    }

    @Test
    void configurationImportValidateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        assertRejectsForInvalidIdentity(principal ->
                configurationImportController.validate("TASK-001", null, principal), configurationImportService);
    }

    @Test
    void fcVersionCreateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        CreateFcVersionRequest request = new CreateFcVersionRequest();

        assertRejectsForInvalidIdentity(principal -> fcVersionController.create(request, principal),
                fcVersionCommandService);
    }

    @Test
    void fcVersionUploadRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        assertRejectsForInvalidIdentity(principal -> fcVersionController.upload(
                "FC-001", "BASE", "2026-01", null, principal), fcVersionCommandService);
    }

    @Test
    void fcVersionPublishRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        assertRejectsForInvalidIdentity(principal -> fcVersionController.publish(
                "FC-001", "BASE", "2026-01", principal), fcVersionCommandService);
    }

    @Test
    void calculationCalculateRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        CalculateRequest request = new CalculateRequest();

        assertRejectsForInvalidIdentity(principal -> calculationController.calculate(request, principal),
                calculationService);
    }

    @Test
    void calculationGetRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        assertRejectsForInvalidIdentity(principal -> calculationController.get("BATCH-001", principal),
                calculationService);
    }

    @Test
    void calculationConfirmRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        ResultLifecycleRequest request = new ResultLifecycleRequest();

        assertRejectsForInvalidIdentity(principal -> calculationController.confirm(request, principal),
                calculationService);
    }

    @Test
    void calculationPublishRejectsMissingOrEmptyPrincipalBeforeServiceInteraction() {
        ResultLifecycleRequest request = new ResultLifecycleRequest();

        assertRejectsForInvalidIdentity(principal -> calculationController.publish(request, principal),
                calculationService);
    }

    private void assertRejectsForInvalidIdentity(Consumer<Principal> invocation, Object service) {
        assertRejects(() -> invocation.accept(null), service);
        // Principal 对象存在但 getName() 为 null 时，也必须在 Service 调用前拒绝请求。
        assertRejects(() -> invocation.accept(() -> null), service);
        assertRejects(() -> invocation.accept(() -> ""), service);
        assertRejects(() -> invocation.accept(() -> "   "), service);
    }

    private void assertRejects(Executable invocation, Object service) {
        assertThrows(IllegalStateException.class, invocation);
        verifyNoInteractions(service);
    }
}
