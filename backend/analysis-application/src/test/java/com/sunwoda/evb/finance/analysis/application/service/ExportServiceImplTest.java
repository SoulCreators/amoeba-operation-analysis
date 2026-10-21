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
        // 本次修改：下载先读取归属和生命周期，再把当前认证身份传给文件端口。
        port.statusJob = succeededJob();
        port.downloadSnapshot = authorizedSnapshot();
        service.download(" U001 ", " EXP-001 ");

        assertEquals("U001", port.lastUserId);
        assertEquals("EXP-001", port.lastJobId);
    }

    @Test
    void downloadRechecksCurrentExportScopeBeforeOpeningFile() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("OTHER"), port);
        port.statusJob = succeededJob();
        port.downloadSnapshot = authorizedSnapshot();

        Optional<ExportFile> result = service.download("U001", "EXP-001");

        // 本次修改：当前用户已失去导出范围时，下载返回统一空结果且不得访问文件端口。
        assertFalse(result.isPresent());
        assertFalse(port.downloadCalled);
    }

    @Test
    void downloadRechecksCurrentDetailScopeBeforeOpeningFile() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);
        port.statusJob = succeededJob();
        port.downloadSnapshot = snapshotWithDetailScope();

        Optional<ExportFile> result = service.download("U001", "EXP-001");

        // 本次修改：当前仅保留汇总导出、已失去明细权限时，同样禁止读取含明细文件。
        assertFalse(result.isPresent());
        assertFalse(port.downloadCalled);
    }

    @Test
    void downloadRequiresOwnedSucceededAndUnexpiredTask() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);
        port.statusJob = ExportJob.queued("EXP-001", "OTHER", "REQ-001",
                "B-001-R3.xlsx", ExportFormat.XLSX.getContentType(), LocalDateTime.now());
        port.downloadSnapshot = authorizedSnapshot();

        Optional<ExportFile> result = service.download("U001", "EXP-001");

        // 本次修改：任务归属不匹配时在任何授权快照和文件访问前失败关闭。
        assertFalse(result.isPresent());
        assertFalse(port.downloadCalled);
    }

    @Test
    void downloadRejectsTaskThatIsNotSucceeded() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);
        port.statusJob = ExportJob.queued("EXP-001", "U001", "REQ-001",
                "B-001-R3.xlsx", ExportFormat.XLSX.getContentType(), LocalDateTime.now());
        port.downloadSnapshot = authorizedSnapshot();

        Optional<ExportFile> result = service.download("U001", "EXP-001");

        // 本次修改：任务尚未成功完成时，不允许绕过状态门禁读取文件。
        assertFalse(result.isPresent());
        assertFalse(port.downloadCalled);
    }

    @Test
    void authorizedCompletedDownloadRemainsAvailable() {
        RecordingTaskPort port = new RecordingTaskPort();
        ExportService service = new ExportServiceImpl(policyFor("U001"), port);
        port.statusJob = succeededJob();
        port.downloadSnapshot = authorizedSnapshot();
        port.downloadResult = Optional.of(new ExportFile("result.xlsx",
                ExportFormat.XLSX.getContentType(), new byte[] {1, 2}));

        Optional<ExportFile> result = service.download("U001", "EXP-001");

        // 本次修改：合法的本人、已完成且仍有范围授权的下载行为保持不变。
        assertTrue(result.isPresent());
        assertTrue(port.downloadCalled);
        assertEquals("result.xlsx", result.get().getFileName());
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

    private ExportJob succeededJob() {
        ExportJob queued = ExportJob.queued("EXP-001", "U001", "REQ-001",
                "B-001-R3.xlsx", ExportFormat.XLSX.getContentType(), LocalDateTime.now());
        return queued.succeeded("B-001-R3.xlsx", ExportFormat.XLSX.getContentType(),
                LocalDateTime.now());
    }

    private ExportTaskSnapshot authorizedSnapshot() {
        return new ExportTaskSnapshot("B-001", "R3", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED, ExportFormat.XLSX,
                Collections.singleton("NANCHANG"), Collections.<String>emptySet());
    }

    private ExportTaskSnapshot snapshotWithDetailScope() {
        return new ExportTaskSnapshot("B-001", "R3", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED, ExportFormat.XLSX,
                Collections.singleton("NANCHANG"), Collections.singleton("NANCHANG"));
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
        private ExportJob statusJob;
        private ExportTaskSnapshot downloadSnapshot;
        private boolean downloadCalled;
        private Optional<ExportFile> downloadResult = Optional.empty();

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
            return statusJob == null ? Optional.<ExportJob>empty() : Optional.of(statusJob);
        }

        @Override
        public Optional<ExportTaskSnapshot> snapshot(String jobId) {
            return downloadSnapshot == null
                    ? Optional.<ExportTaskSnapshot>empty() : Optional.of(downloadSnapshot);
        }

        @Override
        public Optional<ExportFile> download(String jobId, String userId) {
            lastJobId = jobId;
            lastUserId = userId;
            downloadCalled = true;
            return downloadResult;
        }

        @Override public void markRunning(String jobId) { }
        @Override public void complete(String jobId, String fileName, String contentType,
                                       byte[] content) { }
        @Override public void fail(String jobId, String errorMessage) { }
    }
}
