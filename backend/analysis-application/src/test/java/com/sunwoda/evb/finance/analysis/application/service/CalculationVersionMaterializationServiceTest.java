package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationVersionMaterializationCommand;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 计算快照与版本头物化应用服务单元测试。
 *
 * <p>本次新增：验证固定写入顺序、关联不一致的失败关闭和仓储异常原样传播，
 * 不启动 Spring、不连接数据库，也不触碰 CalculationService 及其他写集。</p>
 */
class CalculationVersionMaterializationServiceTest {
    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 10, 21, 11, 0);

    @Test
    void savesSnapshotBeforeVersionAndReturnsVersionRepositoryResult() {
        List<String> calls = new ArrayList<String>();
        CalculationSnapshot snapshot = snapshot("snapshot-001");
        CalculationVersion version = version("snapshot-001");
        CalculationVersion persistedVersion = version("snapshot-001");
        RecordingSnapshotRepository snapshots = new RecordingSnapshotRepository(calls);
        RecordingVersionRepository versions = new RecordingVersionRepository(calls, persistedVersion);

        CalculationVersion result = service(snapshots, versions).materialize(
                new CalculationVersionMaterializationCommand(snapshot, version));

        assertEquals("snapshot-001", snapshots.saved.getSnapshotId());
        assertSame(persistedVersion, result);
        assertEquals(2, calls.size());
        assertEquals("snapshot", calls.get(0));
        assertEquals("version", calls.get(1));
    }

    @Test
    void rejectsMismatchedSnapshotBeforeAnyRepositoryWrite() {
        List<String> calls = new ArrayList<String>();
        RecordingSnapshotRepository snapshots = new RecordingSnapshotRepository(calls);
        RecordingVersionRepository versions = new RecordingVersionRepository(calls, version("snapshot-002"));

        assertThrows(IllegalArgumentException.class,
                () -> service(snapshots, versions).materialize(
                        new CalculationVersionMaterializationCommand(
                                snapshot("snapshot-001"), version("snapshot-002"))));

        assertEquals(Collections.<String>emptyList(), calls);
    }

    @Test
    void propagatesVersionRepositoryFailureWithoutWrapping() {
        List<String> calls = new ArrayList<String>();
        RuntimeException failure = new RuntimeException("version repository unavailable");
        RecordingSnapshotRepository snapshots = new RecordingSnapshotRepository(calls);
        FailingVersionRepository versions = new FailingVersionRepository(calls, failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service(snapshots, versions).materialize(
                        new CalculationVersionMaterializationCommand(
                                snapshot("snapshot-001"), version("snapshot-001"))));

        assertSame(failure, thrown);
        assertEquals(2, calls.size());
        assertEquals("snapshot", calls.get(0));
        assertEquals("version", calls.get(1));
    }

    private CalculationVersionMaterializationService service(
            CalculationSnapshotRepository snapshots, CalculationVersionRepository versions) {
        return new CalculationVersionMaterializationService(snapshots, versions);
    }

    private CalculationSnapshot snapshot(String id) {
        return new CalculationSnapshot(id, "CALCULATION_INPUT", "1", "{}", "[]",
                "content-hash-001", "source-hash-001", "rule-001", "calculator", CALCULATED_AT);
    }

    private CalculationVersion version(String snapshotId) {
        return new CalculationVersion("version-001", "B-001", AnalysisPerspective.BASE, "V1",
                "job-001", snapshotId, "source-hash-001", "config-hash-001", "input-hash-001",
                CalculationVersionStatus.CALCULATED, true, null, "trace-001", "calculator",
                CALCULATED_AT, null, null, null, null);
    }

    private static class RecordingSnapshotRepository implements CalculationSnapshotRepository {
        protected final List<String> calls;
        private CalculationSnapshot saved;

        private RecordingSnapshotRepository(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public CalculationSnapshot save(CalculationSnapshot snapshot) {
            calls.add("snapshot");
            saved = snapshot;
            return snapshot;
        }

        @Override
        public Optional<CalculationSnapshot> findById(String snapshotId) {
            return Optional.empty();
        }
    }

    private static class RecordingVersionRepository implements CalculationVersionRepository {
        protected final List<String> calls;
        private final CalculationVersion persisted;

        private RecordingVersionRepository(List<String> calls, CalculationVersion persisted) {
            this.calls = calls;
            this.persisted = persisted;
        }

        @Override
        public CalculationVersion save(CalculationVersion version) {
            calls.add("version");
            return persisted;
        }

        @Override
        public Optional<CalculationVersion> findById(String calculationVersionId) {
            return Optional.empty();
        }

        @Override
        public Optional<CalculationVersion> findCurrentByBatchNo(String batchNo,
                                                                  AnalysisPerspective perspective) {
            return Optional.empty();
        }

        @Override
        public List<CalculationVersion> findByBatchNo(String batchNo,
                                                       AnalysisPerspective perspective) {
            return Collections.emptyList();
        }
    }

    private static class FailingVersionRepository extends RecordingVersionRepository {
        private final RuntimeException failure;

        private FailingVersionRepository(List<String> calls, RuntimeException failure) {
            super(calls, null);
            this.failure = failure;
        }

        @Override
        public CalculationVersion save(CalculationVersion version) {
            super.calls.add("version");
            throw failure;
        }
    }
}
