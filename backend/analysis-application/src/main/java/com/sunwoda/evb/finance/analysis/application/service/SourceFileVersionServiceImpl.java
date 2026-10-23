package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.SourceFileVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 源文件版本治理应用服务实现。
 *
 * <p>三条治理规则：</p>
 * <ul>
 *   <li>长期保留：只追加 {@link #register}，旧版本不删除；</li>
 *   <li>生效指针：{@link #activate} 在事务内「先失效后生效」，同对象至多一个生效版本；</li>
 *   <li>重算申请：批次已发布或重复文件哈希时，必须携带 {@code replaceReason}，否则拒绝。</li>
 * </ul>
 */
@Service
public class SourceFileVersionServiceImpl implements SourceFileVersionService {
    private final AnalysisBatchRepository batchRepository;
    private final SourceFileVersionRepository versionRepository;

    public SourceFileVersionServiceImpl(AnalysisBatchRepository batchRepository,
                                        SourceFileVersionRepository versionRepository) {
        this.batchRepository = batchRepository;
        this.versionRepository = versionRepository;
    }

    @Override
    @Transactional
    public SourceFileVersion register(String batchNo, String datasetCode, String objectCode,
                                      String templateCode, String templateVersion, String fileHash,
                                      String objectKey, long rowCount, String replaceReason,
                                      String uploadedBy) {
        requireText(batchNo, "batchNo");
        requireText(datasetCode, "datasetCode");
        requireText(objectCode, "objectCode");
        requireText(templateCode, "templateCode");
        requireText(fileHash, "fileHash");
        requireText(uploadedBy, "uploadedBy");
        if (rowCount < 0) throw new IllegalArgumentException("行数不能为负数");

        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        String period = batch.getPeriod();

        boolean published = batch.getStatus() == BatchStatus.PUBLISHED;
        boolean duplicate = versionRepository.existsByObjectAndHash(period, objectCode, fileHash);
        if (published && blank(replaceReason)) {
            throw new IllegalStateException("批次已发布，重传需提供重算申请原因");
        }
        if (duplicate && blank(replaceReason)) {
            throw new IllegalStateException("该对象已存在相同哈希的文件版本，重复上传需说明原因");
        }

        int versionNo = versionRepository.nextVersionNo(period, objectCode) + 1;
        String fileId = "SFV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        SourceFileVersion version = SourceFileVersion.create(fileId, batchNo, period, datasetCode,
                objectCode, versionNo, templateCode, templateVersion, fileHash, objectKey,
                rowCount, uploadedBy);
        return versionRepository.save(version);
    }

    @Override
    @Transactional
    public SourceFileVersion activate(String fileId) {
        requireText(fileId, "fileId");
        SourceFileVersion version = versionRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("源文件版本不存在: " + fileId));
        if (version.isActive()) {
            return version;
        }
        versionRepository.switchActive(version.getPeriod(), version.getObjectCode(), fileId);
        return versionRepository.findByFileId(fileId)
                .orElseThrow(() -> new IllegalStateException("源文件版本激活后不可见: " + fileId));
    }

    @Override
    public List<SourceFileVersion> listVersions(String period, String objectCode) {
        requireText(period, "period");
        requireText(objectCode, "objectCode");
        return versionRepository.findByPeriodAndObject(period, objectCode);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
