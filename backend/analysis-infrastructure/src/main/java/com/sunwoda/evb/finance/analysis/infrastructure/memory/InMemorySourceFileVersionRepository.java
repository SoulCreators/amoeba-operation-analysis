package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.SourceFileVersionRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 源文件版本内存仓储，用于隔离切片与测试。
 *
 * <p>按 fileId 唯一键存储，切换生效指针时先失效后生效，模拟部分唯一索引语义。</p>
 */
@Repository
public class InMemorySourceFileVersionRepository implements SourceFileVersionRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<String, SourceFileVersion> byFileId =
            new LinkedHashMap<String, SourceFileVersion>();

    @Override
    public synchronized SourceFileVersion save(SourceFileVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (version.getId() == null) {
            SourceFileVersion assigned = SourceFileVersion.restore(sequence.getAndIncrement(),
                    version.getFileId(), version.getBatchNo(), version.getPeriod(),
                    version.getDatasetCode(), version.getObjectCode(), version.getVersionNo(),
                    version.getTemplateCode(), version.getTemplateVersion(), version.getFileHash(),
                    version.getObjectKey(), version.getRowCount(), version.getValidationStatus(),
                    version.isActive(), version.getUploadedBy(), version.getUploadedAt());
            byFileId.put(assigned.getFileId(), assigned);
            return assigned;
        }
        byFileId.put(version.getFileId(), version);
        return version;
    }

    @Override
    public synchronized Optional<SourceFileVersion> findByFileId(String fileId) {
        return Optional.ofNullable(byFileId.get(requiredText(fileId, "fileId")));
    }

    @Override
    public synchronized Optional<SourceFileVersion> findActive(String period, String objectCode) {
        String requiredPeriod = requiredText(period, "period");
        String requiredObject = requiredText(objectCode, "objectCode");
        for (SourceFileVersion version : byFileId.values()) {
            if (requiredPeriod.equals(version.getPeriod())
                    && requiredObject.equals(version.getObjectCode()) && version.isActive()) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<SourceFileVersion> findByPeriodAndObject(String period, String objectCode) {
        String requiredPeriod = requiredText(period, "period");
        String requiredObject = requiredText(objectCode, "objectCode");
        List<SourceFileVersion> result = new ArrayList<SourceFileVersion>();
        for (SourceFileVersion version : byFileId.values()) {
            if (requiredPeriod.equals(version.getPeriod())
                    && requiredObject.equals(version.getObjectCode())) {
                result.add(version);
            }
        }
        result.sort((a, b) -> Integer.compare(a.getVersionNo(), b.getVersionNo()));
        return result;
    }

    @Override
    public synchronized int nextVersionNo(String period, String objectCode) {
        int max = 0;
        for (SourceFileVersion version : findByPeriodAndObject(period, objectCode)) {
            max = Math.max(max, version.getVersionNo());
        }
        return max;
    }

    @Override
    public synchronized boolean existsByObjectAndHash(String period, String objectCode, String fileHash) {
        String requiredPeriod = requiredText(period, "period");
        String requiredObject = requiredText(objectCode, "objectCode");
        String requiredHash = requiredText(fileHash, "fileHash");
        for (SourceFileVersion version : byFileId.values()) {
            if (requiredPeriod.equals(version.getPeriod())
                    && requiredObject.equals(version.getObjectCode())
                    && requiredHash.equals(version.getFileHash())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void switchActive(String period, String objectCode, String fileId) {
        String requiredPeriod = requiredText(period, "period");
        String requiredObject = requiredText(objectCode, "objectCode");
        String requiredFileId = requiredText(fileId, "fileId");
        for (SourceFileVersion version : byFileId.values()) {
            if (requiredPeriod.equals(version.getPeriod())
                    && requiredObject.equals(version.getObjectCode())) {
                version.deactivate();
            }
        }
        SourceFileVersion target = byFileId.get(requiredFileId);
        if (target == null) {
            throw new IllegalArgumentException("源文件版本不存在: " + requiredFileId);
        }
        target.activate();
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
