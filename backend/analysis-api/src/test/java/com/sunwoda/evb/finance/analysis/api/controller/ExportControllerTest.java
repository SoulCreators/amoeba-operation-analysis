package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateExportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ExportDownloadResponse;
import com.sunwoda.evb.finance.analysis.api.dto.ExportTaskResponse;
import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ExportService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExportControllerTest {
    private ExportService service;
    private ExportController controller;

    @BeforeEach
    void setUp() {
        service = mock(ExportService.class);
        controller = new ExportController(service);
    }

    @Test
    void rejectsSubmissionWhenPrincipalIsMissing() {
        assertThrows(IllegalStateException.class,
                () -> controller.create(new CreateExportRequest(), null));

        verifyNoInteractions(service);
    }

    @Test
    void usesPrincipalIdentityForSubmission() {
        CreateExportRequest request = new CreateExportRequest();
        request.setRequestKey("REQ-001");
        request.setBatchNo("BATCH-001");
        request.setResultVersion("V1");
        request.setPerspective("BASE");
        request.setFormat("CSV");
        request.setResultStatus("PUBLISHED");
        ExportJob job = ExportJob.queued("EXP-001", "principal-user", "REQ-001",
                "BATCH-001-V1.csv", "text/csv", LocalDateTime.now());
        when(service.create(org.mockito.ArgumentMatchers.eq("principal-user"),
                org.mockito.ArgumentMatchers.any(ExportRequest.class))).thenReturn(job);

        ApiResult<ExportTaskResponse> result = controller.create(request,
                () -> "  principal-user  ");

        assertTrue(result.isSuccess());
        assertEquals("OK", result.getCode());
        assertEquals("EXP-001", result.getData().getTaskId());
        org.mockito.ArgumentCaptor<ExportRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ExportRequest.class);
        verify(service).create(org.mockito.ArgumentMatchers.eq("principal-user"), captor.capture());
        assertEquals("REQ-001", captor.getValue().getRequestKey());
        assertEquals("BATCH-001", captor.getValue().getBatchNo());
        assertEquals(AnalysisPerspective.BASE, captor.getValue().getPerspective());
        assertEquals(CalculationResultStatus.PUBLISHED, captor.getValue().getResultStatus());
    }

    @Test
    void usesPrincipalIdentityForStatusAndDownload() {
        ExportJob job = ExportJob.queued("EXP-001", "principal-user", "REQ-001",
                "BATCH-001-V1.csv", "text/csv", LocalDateTime.now());
        when(service.status("principal-user", "EXP-001")).thenReturn(java.util.Optional.of(job));
        ExportFile file = new ExportFile("result.csv", "text/csv", new byte[] {1, 2});
        when(service.download("principal-user", "EXP-001")).thenReturn(java.util.Optional.of(file));
        Principal principal = () -> "principal-user";

        assertEquals("EXP-001", controller.get("EXP-001", principal).getData().getTaskId());
        assertEquals("result.csv", controller.download("EXP-001", principal).getData().getFileName());
        verify(service).status("principal-user", "EXP-001");
        verify(service).download("principal-user", "EXP-001");
    }
}
