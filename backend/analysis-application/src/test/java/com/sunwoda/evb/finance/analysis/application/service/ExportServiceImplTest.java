package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceImplTest {
    @Test
    void authorizesBeforeCreatingAndUsesOnlyAuthenticatedUserId() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);

        ExportJob job = service.create(" U001 ", request());

        assertEquals("U001", port.createdUserId);
        assertEquals("REQ-001", port.createdRequestKey);
        assertEquals("B-001-R3.xlsx", port.createdFileName);
        assertEquals(ExportFormat.XLSX.getContentType(), port.createdContentType);
        assertEquals("B-001", port.createdSnapshot.getBatchNo());
        assertEquals("R3", port.createdSnapshot.getResultVersion());
        assertEquals(AnalysisPerspective.BASE, port.createdSnapshot.getPerspective());
        assertEquals(CalculationResultStatus.PUBLISHED,
                port.createdSnapshot.getResultStatus());
        assertEquals(Collections.singleton("NANCHANG"),
                port.createdSnapshot.getSummaryScopes());
        assertTrue(port.createdSnapshot.getDetailScopes().isEmpty());
        assertEquals("EXP-001", job.getJobId());
    }

    @Test
    void deniedPermissionNeverCreatesTask() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("OTHER"), port);

        assertThrows(ExportAuthorizationException.class,
                () -> service.create("U001", request()));
        assertFalse(port.created);
    }

    @Test
    void statusAndDownloadPassAuthenticatedUserIdToPort() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);

        service.status(" U001 ", " EXP-001 ");
        service.download(" U001 ", " EXP-001 ");

        assertEquals("U001", port.lastUserId);
        assertEquals("EXP-001", port.lastJobId);
    }

    @Test
    void requestValidatesRequiredExportFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExportRequest(" ", "B-001", "R1",
                        AnalysisPerspective.BASE, ExportFormat.CSV));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportRequest("REQ-001", " ", "R1",
                        AnalysisPerspective.BASE, ExportFormat.CSV));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportRequest("REQ-001", "B-001", " ",
                        AnalysisPerspective.BASE, ExportFormat.CSV));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportRequest("REQ-001", "B-001", "R1",
                        null, ExportFormat.CSV));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportRequest("REQ-001", "B-001", "R1",
                        AnalysisPerspective.BASE, null));
    }

    private ExportPermissionPolicy policyFor(final String permittedUserId) {
        return new ExportPermissionPolicy(new PermissionService() {
            @Override public boolean canExport(String userId, AnalysisPerspective perspective,
                                                String scopeCode) {
                return permittedUserId.equals(userId);
            }
            @Override public java.util.Set<String> exportScopes(String userId,
                                                                 AnalysisPerspective perspective) {
                return permittedUserId.equals(userId)
                        ? Collections.singleton("NANCHANG") : Collections.<String>emptySet();
            }
            @Override public boolean canViewDraft(String userId, AnalysisPerspective perspective) {
                return false;
            }
            @Override public boolean canViewDetail(String userId, AnalysisPerspective perspective,
                                                   String scopeCode) { return false; }
            @Override public com.sunwoda.evb.finance.analysis.domain.model.UserScope save(
                    com.sunwoda.evb.finance.analysis.domain.model.UserScope scope) { return scope; }
            @Override public java.util.List<com.sunwoda.evb.finance.analysis.domain.model.UserScope> list(
                    String userId, AnalysisPerspective perspective) { return Collections.emptyList(); }
            @Override public boolean canViewScope(String userId, AnalysisPerspective perspective,
                                                  String scopeCode) { return false; }
            @Override public boolean canEditConfig(String userId, AnalysisPerspective perspective,
                                                   String scopeCode) { return false; }
            @Override public boolean canViewResult(String userId, AnalysisPerspective perspective,
                                                   CalculationResultStatus status) { return false; }
            @Override public boolean canPublishAi(String userId, AnalysisPerspective perspective) {
                return false;
            }
            @Override public java.util.Set<String> viewableScopes(String userId,
                                                                   AnalysisPerspective perspective) {
                return Collections.emptySet();
            }
            @Override public java.util.Set<String> detailScopes(String userId,
                                                                 AnalysisPerspective perspective) {
                return Collections.emptySet();
            }
        });
    }

    private ExportRequest request() {
        return new ExportRequest("REQ-001", "B-001", "R3", AnalysisPerspective.BASE,
                ExportFormat.XLSX, CalculationResultStatus.PUBLISHED);
    }

    private static final class RecordingTaskPort implements ExportTaskPort {
        private boolean created;
        private String createdUserId;
        private String createdRequestKey;
        private String createdFileName;
        private String createdContentType;
        private ExportTaskSnapshot createdSnapshot;
        private String lastUserId;
        private String lastJobId;

        @Override
        public ExportJob create(String userId, String requestKey,
                                ExportTaskSnapshot snapshot) {
            created = true;
            createdUserId = userId;
            createdRequestKey = requestKey;
            createdFileName = snapshot.fileName();
            createdContentType = snapshot.contentType();
            createdSnapshot = snapshot;
            return ExportJob.queued("EXP-001", userId, requestKey,
                    createdFileName, createdContentType, LocalDateTime.now());
        }

        @Override
        public ExportJob create(String userId, String requestKey, String fileName,
                                String contentType) {
            created = true;
            createdUserId = userId;
            createdRequestKey = requestKey;
            createdFileName = fileName;
            createdContentType = contentType;
            return ExportJob.queued("EXP-001", userId, requestKey, fileName, contentType,
                    LocalDateTime.now());
        }

        @Override
        public Optional<ExportJob> status(String jobId, String userId) {
            lastJobId = jobId;
            lastUserId = userId;
            return Optional.empty();
        }

        @Override
        public Optional<ExportFile> download(String jobId, String userId) {
            lastJobId = jobId;
            lastUserId = userId;
            return Optional.empty();
        }

        @Override public void markRunning(String jobId) { }
        @Override public void complete(String jobId, String fileName, String contentType,
                                       byte[] content) { }
        @Override public void fail(String jobId, String errorMessage) { }
    }
}
