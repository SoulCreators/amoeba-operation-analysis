package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportGenerationResult;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportGenerationRenderer;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** GOV-002-EXPORT-GENERATION-TECH-001 应用层生成编排契约测试。 */
class ExportGenerationServiceTest {
    @Test
    void readsSnapshotMarksRunningRendersAndCompletesInOrder() {
        RecordingTaskPort port = new RecordingTaskPort(snapshot());
        ExportGenerationResult expected = new ExportGenerationResult(
                "rendered.bin", "application/test", new byte[] {1, 2, 3});
        ExportGenerationRenderer renderer = input -> {
            assertEquals(snapshot().getBatchNo(), input.getBatchNo());
            assertEquals(Collections.singleton("NANCHANG"), input.getSummaryScopes());
            assertEquals(Arrays.asList("snapshot", "running"), port.events);
            port.events.add("render");
            return expected;
        };

        ExportGenerationResult actual = new ExportGenerationService(port, renderer)
                .generate("EXP-001");

        assertSame(expected, actual);
        assertEquals(Arrays.asList("snapshot", "running", "render", "complete"),
                port.events);
        assertEquals("rendered.bin", port.completedFileName);
        assertEquals("application/test", port.completedContentType);
        assertArrayEquals(new byte[] {1, 2, 3}, port.completedContent);
    }

    @Test
    void rendererFailureMarksFailedAndRethrowsOriginalException() {
        RecordingTaskPort port = new RecordingTaskPort(snapshot());
        IllegalStateException failure = new IllegalStateException("渲染器不可用");
        ExportGenerationService service = new ExportGenerationService(port,
                input -> { throw failure; });

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.generate("EXP-001"));

        assertSame(failure, thrown);
        assertEquals("EXP-001", port.failedJobId);
        assertEquals("渲染器不可用", port.failedMessage);
        assertEquals(Arrays.asList("snapshot", "running", "fail"), port.events);
    }

    /** 验证其他 Worker 已原子领取时只报领取结果，不进入渲染或失败链路。 */
    @Test
    void alreadyClaimedByAnotherWorkerDoesNotRenderOrFail() {
        RecordingTaskPort port = new RecordingTaskPort(snapshot());
        port.tryMarkRunningResult = false;
        ExportGenerationService service = new ExportGenerationService(port,
                input -> { throw new AssertionError("已被领取的任务不应渲染"); });

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.generate("EXP-001"));

        assertEquals("导出任务已被其他 Worker 领取，不能重复渲染: EXP-001", thrown.getMessage());
        assertEquals(Arrays.asList("snapshot", "try-running"), port.events);
        assertNull(port.failedJobId);
    }

    @Test
    void snapshotOrRunningGateFailureDoesNotInvokeRendererOrFail() {
        RecordingTaskPort missingSnapshotPort = new RecordingTaskPort(null);
        ExportGenerationRenderer missingRenderer = input -> {
            throw new AssertionError("快照缺失时不应渲染");
        };
        ExportGenerationService missingSnapshotService = new ExportGenerationService(
                missingSnapshotPort, missingRenderer);

        assertThrows(IllegalStateException.class,
                () -> missingSnapshotService.generate("EXP-MISSING"));
        assertEquals(Collections.singletonList("snapshot"), missingSnapshotPort.events);

        RecordingTaskPort gatePort = new RecordingTaskPort(snapshot());
        gatePort.runningFailure = new IllegalStateException("任务已终态");
        ExportGenerationService gateService = new ExportGenerationService(gatePort,
                input -> { throw new AssertionError("门禁失败时不应渲染"); });

        assertThrows(IllegalStateException.class, () -> gateService.generate("EXP-001"));
        assertEquals(Collections.singletonList("snapshot"), gatePort.events);
        assertNull(gatePort.failedJobId);
    }

    private ExportTaskSnapshot snapshot() {
        return new ExportTaskSnapshot("B-001", "R1", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED, ExportFormat.CSV,
                Collections.singleton("NANCHANG"), Collections.<String>emptySet());
    }

    /** 记录端口调用顺序，验证服务只编排既有生命周期端口。 */
    private static final class RecordingTaskPort implements ExportTaskPort {
        private final ExportTaskSnapshot snapshot;
        private final java.util.List<String> events = new java.util.ArrayList<String>();
        private IllegalStateException runningFailure;
        private boolean tryMarkRunningResult = true;
        private String completedFileName;
        private String completedContentType;
        private byte[] completedContent;
        private String failedJobId;
        private String failedMessage;

        private RecordingTaskPort(ExportTaskSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<ExportTaskSnapshot> snapshot(String jobId) {
            events.add("snapshot");
            return Optional.ofNullable(snapshot);
        }

        @Override
        public void markRunning(String jobId) {
            if (runningFailure != null) throw runningFailure;
            events.add("running");
        }

        @Override
        public boolean tryMarkRunning(String jobId) {
            if (runningFailure != null) throw runningFailure;
            events.add(tryMarkRunningResult ? "running" : "try-running");
            return tryMarkRunningResult;
        }

        @Override
        public void complete(String jobId, String fileName, String contentType, byte[] content) {
            events.add("complete");
            completedFileName = fileName;
            completedContentType = contentType;
            completedContent = content;
        }

        @Override
        public void fail(String jobId, String errorMessage) {
            events.add("fail");
            failedJobId = jobId;
            failedMessage = errorMessage;
        }

        @Override
        public ExportJob create(String userId, String requestKey,
                                String fileName, String contentType) {
            return ExportJob.queued("EXP-001", userId, requestKey, fileName, contentType,
                    LocalDateTime.now());
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
