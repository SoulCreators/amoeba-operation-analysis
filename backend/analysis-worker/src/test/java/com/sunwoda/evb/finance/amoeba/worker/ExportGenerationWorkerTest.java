package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportGenerationResult;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import com.sunwoda.evb.finance.analysis.application.service.ExportGenerationService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ExportGenerationWorker 的独立委托契约测试。
 *
 * <p>本测试只验证 Worker 入口的参数边界和应用服务委托，不启动 Spring，
 * 也不触碰数据库、文件系统、权限或格式生成逻辑。</p>
 */
class ExportGenerationWorkerTest {

    @Test
    void processDelegatesNormalizedJobIdToApplicationService() {
        RecordingTaskPort taskPort = new RecordingTaskPort();
        ExportGenerationResult expected = new ExportGenerationResult(
                "result.csv", "text/csv", new byte[] {1, 2, 3});
        ExportGenerationService service = new ExportGenerationService(taskPort,
                snapshot -> expected);
        ExportGenerationWorker worker = new ExportGenerationWorker(service);

        worker.process("  EXP-001  ");

        assertEquals("EXP-001", taskPort.completedJobId);
    }

    @Test
    void processRejectsBlankJobIdBeforeCallingApplicationService() {
        RecordingTaskPort taskPort = new RecordingTaskPort();
        ExportGenerationService service = new ExportGenerationService(taskPort,
                snapshot -> {
                    throw new AssertionError("空任务号不应进入应用服务");
                });
        ExportGenerationWorker worker = new ExportGenerationWorker(service);

        assertThrows(IllegalArgumentException.class, () -> worker.process("  "));
        assertNull(taskPort.completedJobId);
    }

    private static ExportTaskSnapshot snapshotFixture() {
        return new ExportTaskSnapshot("B-001", "R1", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED, ExportFormat.CSV,
                Collections.<String>emptySet(), Collections.<String>emptySet());
    }

    private static final class RecordingTaskPort implements ExportTaskPort {
        private String completedJobId;

        @Override
        public Optional<ExportTaskSnapshot> snapshot(String jobId) {
            return Optional.of(snapshotFixture());
        }

        @Override
        public void markRunning(String jobId) {
        }

        @Override
        public void complete(String jobId, String fileName, String contentType, byte[] content) {
            completedJobId = jobId;
        }

        @Override
        public void fail(String jobId, String errorMessage) {
            throw new AssertionError("本测试不应进入失败路径");
        }

        @Override
        public ExportJob create(String userId, String requestKey,
                                String fileName, String contentType) {
            return ExportJob.queued("EXP-001", userId, requestKey, fileName,
                    contentType, LocalDateTime.now());
        }

        @Override
        public Optional<ExportJob> status(String jobId, String userId) {
            return Optional.empty();
        }

        @Override
        public Optional<ExportFile> download(String jobId, String userId) {
            return Optional.empty();
        }
    }
}
