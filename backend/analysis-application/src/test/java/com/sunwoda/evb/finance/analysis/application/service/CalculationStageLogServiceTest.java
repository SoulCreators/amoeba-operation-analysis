package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationJobDetail;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationStageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计算作业阶段日志与心跳应用服务契约。 */
class CalculationStageLogServiceTest {
    private static final String JOB_ID = "JOB-STAGE-001";
    private static final String BATCH_NO = "BATCH-STAGE-001";

    private FakeJobRepository jobRepository;
    private FakeStageLogRepository stageLogRepository;
    private CalculationStageLogService service;

    @BeforeEach
    void setUp() {
        jobRepository = new FakeJobRepository();
        stageLogRepository = new FakeStageLogRepository();
        service = new CalculationStageLogServiceImpl(jobRepository, stageLogRepository);
        jobRepository.save(new CalculationJob(JOB_ID, "REQ-STAGE-001", BATCH_NO, LocalDateTime.now()));
    }

    @Test
    void stageStartedRecordsRunningLogAndMovesJobStage() {
        service.recordStageStarted(JOB_ID, CalculationStage.NORMALIZE, "trace-1");

        CalculationJob job = jobRepository.findByJobId(JOB_ID).orElseThrow(AssertionError::new);
        assertEquals(CalculationStage.NORMALIZE, job.getStage());
        assertEquals("trace-1", job.getTraceId());
        assertNotNull(job.getHeartbeatAt());

        CalculationStageLog log = stageLogRepository.findByJobId(JOB_ID).get(0);
        assertEquals(CalculationStage.NORMALIZE, log.getStage());
        assertEquals(CalculationStageStatus.RUNNING, log.getStatus());
        assertEquals("trace-1", log.getTraceId());
        assertNull(log.getFinishedAt());
    }

    @Test
    void heartbeatUpdatesJobMetricsWithoutFinalizingStage() {
        service.recordStageStarted(JOB_ID, CalculationStage.ALLOCATE, "trace-2");
        service.recordHeartbeat(JOB_ID, CalculationStage.ALLOCATE, 500L, 3);

        CalculationJob job = jobRepository.findByJobId(JOB_ID).orElseThrow(AssertionError::new);
        assertEquals(CalculationStage.ALLOCATE, job.getStage());
        assertEquals(500L, job.getProcessedRows());
        assertEquals(3, job.getIssueCount());

        CalculationStageLog log = stageLogRepository.findByJobId(JOB_ID).get(0);
        assertEquals(CalculationStageStatus.RUNNING, log.getStatus());
        assertNull(log.getFinishedAt());
    }

    @Test
    void stageSucceededFinalizesLogWithMetricsAndControlAmount() {
        service.recordStageStarted(JOB_ID, CalculationStage.PNL, "trace-3");
        service.recordStageSucceeded(JOB_ID, CalculationStage.PNL, 1000L, new BigDecimal("1234567.89"), 2);

        CalculationStageLog log = stageLogRepository.findByJobId(JOB_ID).get(0);
        assertEquals(CalculationStageStatus.SUCCEEDED, log.getStatus());
        assertEquals(1000L, log.getProcessedRows());
        assertEquals(new BigDecimal("1234567.89"), log.getControlAmount());
        assertEquals(2, log.getIssueCount());
        assertEquals("trace-3", log.getTraceId());
        assertNotNull(log.getFinishedAt());
    }

    @Test
    void stageFailedPreservesErrorCodeAndMessageAndDoesNotSilentlyZeroAmounts() {
        service.recordStageStarted(JOB_ID, CalculationStage.RECONCILE, "trace-4");
        service.recordStageFailed(JOB_ID, CalculationStage.RECONCILE, "R001", "勾稽差异未通过");

        CalculationStageLog log = stageLogRepository.findByJobId(JOB_ID).get(0);
        assertEquals(CalculationStageStatus.FAILED, log.getStatus());
        assertEquals("R001", log.getErrorCode());
        assertEquals("勾稽差异未通过", log.getMessage());

        CalculationJob job = jobRepository.findByJobId(JOB_ID).orElseThrow(AssertionError::new);
        assertEquals(CalculationStage.RECONCILE, job.getStage());
        assertEquals("R001", job.getErrorCode());
        // 阶段失败不改变作业整体生命周期状态，也不产生部分正式金额。
        assertEquals(CalculationJobStatus.QUEUED, job.getStatus());
    }

    @Test
    void getJobDetailReturnsOrderedStageLogsAndRetryability() {
        service.recordStageStarted(JOB_ID, CalculationStage.NORMALIZE, "trace-5");
        service.recordStageSucceeded(JOB_ID, CalculationStage.NORMALIZE, 10L, null, 0);
        service.recordStageStarted(JOB_ID, CalculationStage.MAP, "trace-5");
        service.recordStageFailed(JOB_ID, CalculationStage.MAP, "M001", "映射失败");

        // 作业整体仍未 FAILED，可重试性为 false（生命周期状态由计算编排负责）。
        CalculationJobDetail detail = service.getJobDetail(JOB_ID);
        assertNotNull(detail.getJob());
        List<CalculationStageLog> logs = detail.getStageLogs();
        assertEquals(2, logs.size());
        assertEquals(CalculationStage.NORMALIZE, logs.get(0).getStage());
        assertEquals(CalculationStage.MAP, logs.get(1).getStage());
        assertFalse(detail.isRetryable());

        // 作业整体失败后可重试性为 true。
        jobRepository.findByJobId(JOB_ID).orElseThrow(AssertionError::new).moveTo(CalculationJobStatus.RUNNING);
        jobRepository.findByJobId(JOB_ID).orElseThrow(AssertionError::new).fail("E001", "整体失败");
        assertTrue(service.getJobDetail(JOB_ID).isRetryable());
    }

    @Test
    void unknownJobRejectsAllStageOperations() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordStageStarted("MISSING-JOB", CalculationStage.MAP, null));
        assertThrows(IllegalArgumentException.class, () -> service.getJobDetail("MISSING-JOB"));
    }

    /** 应用层测试自带的内存作业仓储，避免依赖基础设施模块。 */
    private static final class FakeJobRepository implements CalculationJobRepository {
        private final Map<String, CalculationJob> store = new LinkedHashMap<String, CalculationJob>();

        @Override
        public CalculationJob save(CalculationJob job) {
            if (job == null) {
                throw new IllegalArgumentException("job 不能为空");
            }
            store.put(job.getJobId(), job);
            return job;
        }

        @Override
        public Optional<CalculationJob> findByJobId(String jobId) {
            return Optional.ofNullable(store.get(jobId));
        }

        @Override
        public Optional<CalculationJob> findActiveByRequestKey(String requestKey) {
            for (CalculationJob job : store.values()) {
                if (requestKey.equals(job.getRequestKey())
                        && (job.getStatus() == CalculationJobStatus.QUEUED
                        || job.getStatus() == CalculationJobStatus.RUNNING)) {
                    return Optional.of(job);
                }
            }
            return Optional.empty();
        }
    }

    /** 应用层测试自带的内存阶段日志仓储，按 (jobId, stage) 幂等覆盖并按插入顺序返回。 */
    private static final class FakeStageLogRepository implements CalculationStageLogRepository {
        private final Map<String, CalculationStageLog> byJobStage =
                new LinkedHashMap<String, CalculationStageLog>();

        @Override
        public CalculationStageLog save(CalculationStageLog log) {
            if (log == null) {
                throw new IllegalArgumentException("log 不能为空");
            }
            byJobStage.put(log.getJobId() + ' ' + log.getStage().name(), log);
            return log;
        }

        @Override
        public List<CalculationStageLog> findByJobId(String jobId) {
            List<CalculationStageLog> result = new ArrayList<CalculationStageLog>();
            for (CalculationStageLog log : byJobStage.values()) {
                if (jobId.equals(log.getJobId())) {
                    result.add(log);
                }
            }
            return result;
        }
    }
}
