package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class ImportTask {
    private final Long id;
    private final String taskNo;
    private final String batchNo;
    private final String datasetCode;
    private final String fileName;
    private final String checksum;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private ImportStatus status;
    private int issueCount;
    private LocalDateTime updatedAt;

    public ImportTask(Long id, String taskNo, String batchNo, String datasetCode,
                      String fileName, String checksum, String createdBy,
                      LocalDateTime createdAt) {
        this.id = id;
        this.taskNo = taskNo;
        this.batchNo = batchNo;
        this.datasetCode = datasetCode;
        this.fileName = fileName;
        this.checksum = checksum;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = ImportStatus.CREATED;
    }

    public void moveTo(ImportStatus next, int issueCount) {
        if (next == null) throw new IllegalArgumentException("导入状态不能为空");
        if (issueCount < 0) throw new IllegalArgumentException("问题数不能为负数");
        if (status == ImportStatus.CANCELLED && next != ImportStatus.CANCELLED) {
            throw new IllegalStateException("已取消的导入任务不可恢复");
        }
        if (next == ImportStatus.VALID && issueCount > 0) {
            throw new IllegalStateException("存在校验问题时不可标记为有效");
        }
        this.status = next;
        this.issueCount = issueCount;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTaskNo() { return taskNo; }
    public String getBatchNo() { return batchNo; }
    public String getDatasetCode() { return datasetCode; }
    public String getFileName() { return fileName; }
    public String getChecksum() { return checksum; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ImportStatus getStatus() { return status; }
    public int getIssueCount() { return issueCount; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
