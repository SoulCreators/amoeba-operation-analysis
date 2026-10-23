package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内存阶段日志仓储按 (jobId, stage) 幂等覆盖与按执行顺序返回的契约。 */
class InMemoryCalculationStageLogRepositoryTest {
    private InMemoryCalculationStageLogRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCalculationStageLogRepository();
    }

    @Test
    void saveOverwritesSameJobAndStageAndKeepsInsertionOrder() {
        repository.save(CalculationStageLog.start("JOB-1", CalculationStage.NORMALIZE, "t-1"));
        repository.save(CalculationStageLog.start("JOB-1", CalculationStage.MAP, "t-1"));
        repository.save(CalculationStageLog.start("JOB-1", CalculationStage.NORMALIZE, "t-1")
                .succeed(5L, null, 0));

        List<CalculationStageLog> logs = repository.findByJobId("JOB-1");
        assertEquals(2, logs.size());
        assertEquals(CalculationStage.NORMALIZE, logs.get(0).getStage());
        assertEquals(CalculationStageStatus.SUCCEEDED, logs.get(0).getStatus());
        assertEquals(CalculationStage.MAP, logs.get(1).getStage());
    }

    @Test
    void findByJobIdIsolateJobsAndRejectsBlankId() {
        repository.save(CalculationStageLog.start("JOB-1", CalculationStage.NORMALIZE, null));
        repository.save(CalculationStageLog.start("JOB-2", CalculationStage.NORMALIZE, null));

        assertEquals(1, repository.findByJobId("JOB-1").size());
        assertEquals(1, repository.findByJobId("JOB-2").size());
        assertTrue(repository.findByJobId("JOB-3").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> repository.findByJobId("  "));
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }
}
