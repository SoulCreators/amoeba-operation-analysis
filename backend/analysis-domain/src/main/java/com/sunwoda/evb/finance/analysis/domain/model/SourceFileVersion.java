package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 源文件版本（source_file_version）。
 *
 * <p>一条上传文件的长期保留记录：同一期间＋数据对象可累积多个版本号，旧版本只失效
 * 不删除；同一对象在任意时刻至多一个生效（active）版本。生效指针的切换在仓储/事务层
 * 完成，本模型只承载状态。</p>
 */
public class SourceFileVersion {
    private final Long id;
    private final String fileId;
    private final String batchNo;
    private final String period;
    private final String datasetCode;
    private final String objectCode;
    private final int versionNo;
    private final String templateCode;
    private final String templateVersion;
    private final String fileHash;
    private final String objectKey;
    private final long rowCount;
    private SourceFileValidationStatus validationStatus;
    private boolean active;
    private final String uploadedBy;
    private final LocalDateTime uploadedAt;

    private SourceFileVersion(Long id, String fileId, String batchNo, String period,
                              String datasetCode, String objectCode, int versionNo,
                              String templateCode, String templateVersion, String fileHash,
                              String objectKey, long rowCount,
                              SourceFileValidationStatus validationStatus, boolean active,
                              String uploadedBy, LocalDateTime uploadedAt) {
        if (validationStatus == null) throw new IllegalArgumentException("校验状态不能为空");
        if (versionNo < 1) throw new IllegalArgumentException("版本号必须从 1 开始");
        if (rowCount < 0) throw new IllegalArgumentException("行数不能为负数");
        this.id = id;
        this.fileId = requireText(fileId, "fileId");
        this.batchNo = requireText(batchNo, "batchNo");
        this.period = requireText(period, "period");
        this.datasetCode = requireText(datasetCode, "datasetCode");
        this.objectCode = requireText(objectCode, "objectCode");
        this.versionNo = versionNo;
        this.templateCode = requireText(templateCode, "templateCode");
        this.templateVersion = templateVersion;
        this.fileHash = requireText(fileHash, "fileHash");
        this.objectKey = objectKey;
        this.rowCount = rowCount;
        this.validationStatus = validationStatus;
        this.active = active;
        this.uploadedBy = requireText(uploadedBy, "uploadedBy");
        this.uploadedAt = uploadedAt == null ? LocalDateTime.now() : uploadedAt;
    }

    /** 新建一条尚未生效、校验待定的源文件版本。 */
    public static SourceFileVersion create(String fileId, String batchNo, String period,
                                           String datasetCode, String objectCode, int versionNo,
                                           String templateCode, String templateVersion, String fileHash,
                                           String objectKey, long rowCount, String uploadedBy) {
        return new SourceFileVersion(null, fileId, batchNo, period, datasetCode, objectCode,
                versionNo, templateCode, templateVersion, fileHash, objectKey, rowCount,
                SourceFileValidationStatus.PENDING, false, uploadedBy, LocalDateTime.now());
    }

    /** 从持久化存储回放一条已有版本，不复刻其历史状态迁移。 */
    public static SourceFileVersion restore(Long id, String fileId, String batchNo, String period,
                                            String datasetCode, String objectCode, int versionNo,
                                            String templateCode, String templateVersion, String fileHash,
                                            String objectKey, long rowCount,
                                            SourceFileValidationStatus validationStatus, boolean active,
                                            String uploadedBy, LocalDateTime uploadedAt) {
        return new SourceFileVersion(id, fileId, batchNo, period, datasetCode, objectCode,
                versionNo, templateCode, templateVersion, fileHash, objectKey, rowCount,
                validationStatus, active, uploadedBy, uploadedAt);
    }

    /** 标记为生效版本（由仓储/事务负责切换生效指针，不在此处校验唯一性）。 */
    public void activate() { this.active = true; }

    /** 标记为失效版本，旧行保留不删除。 */
    public void deactivate() { this.active = false; }

    public void markValid() { this.validationStatus = SourceFileValidationStatus.VALID; }

    public void markInvalid() { this.validationStatus = SourceFileValidationStatus.INVALID; }

    public Long getId() { return id; }
    public String getFileId() { return fileId; }
    public String getBatchNo() { return batchNo; }
    public String getPeriod() { return period; }
    public String getDatasetCode() { return datasetCode; }
    public String getObjectCode() { return objectCode; }
    public int getVersionNo() { return versionNo; }
    public String getTemplateCode() { return templateCode; }
    public String getTemplateVersion() { return templateVersion; }
    public String getFileHash() { return fileHash; }
    public String getObjectKey() { return objectKey; }
    public long getRowCount() { return rowCount; }
    public SourceFileValidationStatus getValidationStatus() { return validationStatus; }
    public boolean isActive() { return active; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
