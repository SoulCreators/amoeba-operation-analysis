package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.SourceFileVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 源文件版本治理应用服务契约：长期保留、生效指针切换与发布后重算申请门禁。 */
class SourceFileVersionServiceTest {
    private static final String BATCH_NO = "BATCH-SFV-001";
    private static final String PERIOD = "2026-10";
    private static final String DATASET = "DS-SFV";
    private static final String OBJECT = "OBJ-SFV";

    private FakeBatchRepository batchRepository;
    private FakeVersionRepository versionRepository;
    private SourceFileVersionService service;

    @BeforeEach
    void setUp() {
        batchRepository = new FakeBatchRepository();
        versionRepository = new FakeVersionRepository();
        service = new SourceFileVersionServiceImpl(batchRepository, versionRepository);
        batchRepository.save(batch(BatchStatus.DRAFT));
    }

    @Test
    void registerCreatesInactiveVersionWithSequentialVersionNo() {
        SourceFileVersion v1 = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-1", "key-1", 100L, null, "u-1");
        SourceFileVersion v2 = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-2", "key-2", 200L, null, "u-1");

        assertFalse(v1.isActive());
        assertEquals(1, v1.getVersionNo());
        assertEquals(2, v2.getVersionNo());
        assertEquals(2, service.listVersions(PERIOD, OBJECT).size());
    }

    @Test
    void activateSwitchesActivePointerAndRetainsOldVersions() {
        SourceFileVersion v1 = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-1", "key-1", 100L, null, "u-1");
        SourceFileVersion v2 = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v2",
                "hash-2", "key-2", 200L, null, "u-1");

        SourceFileVersion active = service.activate(v1.getFileId());

        assertTrue(active.isActive());
        assertEquals(v1.getFileId(), versionRepository.findActive(PERIOD, OBJECT)
                .orElseThrow(AssertionError::new).getFileId());
        // 旧版本保留且失效：长期保留。
        assertEquals(2, service.listVersions(PERIOD, OBJECT).size());
        assertFalse(versionRepository.findByFileId(v2.getFileId()).orElseThrow(AssertionError::new).isActive());
    }

    @Test
    void activatingAlreadyActiveVersionIsIdempotent() {
        SourceFileVersion v1 = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-1", "key-1", 100L, null, "u-1");
        service.activate(v1.getFileId());
        SourceFileVersion again = service.activate(v1.getFileId());
        assertEquals(v1.getFileId(), again.getFileId());
        assertTrue(again.isActive());
    }

    @Test
    void publishedBatchWithoutReplacementReasonIsRejected() {
        batchRepository.save(batch(BatchStatus.PUBLISHED));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                        "hash-1", "key-1", 100L, null, "u-1"));
        assertEquals("批次已发布，重传需提供重算申请原因", ex.getMessage());
    }

    @Test
    void publishedBatchWithReplacementReasonIsAllowed() {
        batchRepository.save(batch(BatchStatus.PUBLISHED));
        SourceFileVersion v = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-1", "key-1", 100L, "重算申请：修正基地口径", "u-1");
        assertEquals(1, v.getVersionNo());
    }

    @Test
    void duplicateHashWithoutReasonIsRejected() {
        service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1", "hash-1", "key-1", 100L, null, "u-1");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                        "hash-1", "key-2", 100L, null, "u-1"));
        assertEquals("该对象已存在相同哈希的文件版本，重复上传需说明原因", ex.getMessage());
    }

    @Test
    void duplicateHashWithReasonIsAllowed() {
        service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1", "hash-1", "key-1", 100L, null, "u-1");
        SourceFileVersion v = service.register(BATCH_NO, DATASET, OBJECT, "TPL", "v1",
                "hash-1", "key-2", 100L, "重复文件说明", "u-1");
        assertEquals(2, v.getVersionNo());
    }

    @Test
    void unknownBatchAndUnknownFileIdAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register("MISSING-BATCH", DATASET, OBJECT, "TPL", "v1",
                        "hash-1", "key-1", 100L, null, "u-1"));
        assertThrows(IllegalArgumentException.class, () -> service.activate("MISSING-FILE"));
        assertThrows(IllegalArgumentException.class, () -> service.listVersions(" ", OBJECT));
    }

    private static AnalysisBatch batch(BatchStatus status) {
        return new AnalysisBatch(null, BATCH_NO, PERIOD, AnalysisPerspective.BASE, status,
                "u-1", LocalDateTime.now());
    }

    /** 应用层测试自带的内存批次仓储。 */
    private static final class FakeBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> store = new LinkedHashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            store.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(store.get(batchNo));
        }
    }

    /** 应用层测试自带的内存源文件版本仓储，模拟部分唯一索引与生效指针语义。 */
    private static final class FakeVersionRepository implements SourceFileVersionRepository {
        private final AtomicLong sequence = new AtomicLong(1L);
        private final Map<String, SourceFileVersion> store = new LinkedHashMap<String, SourceFileVersion>();

        @Override
        public SourceFileVersion save(SourceFileVersion version) {
            SourceFileVersion assigned = SourceFileVersion.restore(sequence.getAndIncrement(),
                    version.getFileId(), version.getBatchNo(), version.getPeriod(),
                    version.getDatasetCode(), version.getObjectCode(), version.getVersionNo(),
                    version.getTemplateCode(), version.getTemplateVersion(), version.getFileHash(),
                    version.getObjectKey(), version.getRowCount(), version.getValidationStatus(),
                    version.isActive(), version.getUploadedBy(), version.getUploadedAt());
            store.put(assigned.getFileId(), assigned);
            return assigned;
        }

        @Override
        public Optional<SourceFileVersion> findByFileId(String fileId) {
            return Optional.ofNullable(store.get(fileId));
        }

        @Override
        public Optional<SourceFileVersion> findActive(String period, String objectCode) {
            for (SourceFileVersion v : store.values()) {
                if (period.equals(v.getPeriod()) && objectCode.equals(v.getObjectCode()) && v.isActive()) {
                    return Optional.of(v);
                }
            }
            return Optional.empty();
        }

        @Override
        public List<SourceFileVersion> findByPeriodAndObject(String period, String objectCode) {
            List<SourceFileVersion> result = new ArrayList<SourceFileVersion>();
            for (SourceFileVersion v : store.values()) {
                if (period.equals(v.getPeriod()) && objectCode.equals(v.getObjectCode())) {
                    result.add(v);
                }
            }
            result.sort((a, b) -> Integer.compare(a.getVersionNo(), b.getVersionNo()));
            return result;
        }

        @Override
        public int nextVersionNo(String period, String objectCode) {
            int max = 0;
            for (SourceFileVersion v : findByPeriodAndObject(period, objectCode)) {
                max = Math.max(max, v.getVersionNo());
            }
            return max;
        }

        @Override
        public boolean existsByObjectAndHash(String period, String objectCode, String fileHash) {
            for (SourceFileVersion v : store.values()) {
                if (period.equals(v.getPeriod()) && objectCode.equals(v.getObjectCode())
                        && fileHash.equals(v.getFileHash())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void switchActive(String period, String objectCode, String fileId) {
            for (SourceFileVersion v : store.values()) {
                if (period.equals(v.getPeriod()) && objectCode.equals(v.getObjectCode())) {
                    v.deactivate();
                }
            }
            SourceFileVersion target = store.get(fileId);
            if (target == null) {
                throw new IllegalArgumentException("源文件版本不存在: " + fileId);
            }
            target.activate();
        }
    }
}
