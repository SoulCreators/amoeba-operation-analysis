package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

public class ConfigurationImportTask {
    private final Long id;
    private final String taskNo;
    private final ConfigurationImportType type;
    private final String fileName;
    private final String checksum;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private ImportStatus status;
    private LocalDateTime updatedAt;

    public ConfigurationImportTask(Long id, String taskNo, ConfigurationImportType type,
                                   String fileName, String checksum, String createdBy,
                                   LocalDateTime createdAt) {
        this.id = id;
        this.taskNo = taskNo;
        this.type = type;
        this.fileName = fileName;
        this.checksum = checksum;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = ImportStatus.CREATED;
    }

    public void moveTo(ImportStatus next) {
        if (next == null) throw new IllegalArgumentException("配置导入状态不能为空");
        if (status == ImportStatus.CANCELLED && next != ImportStatus.CANCELLED) {
            throw new IllegalStateException("已取消的配置导入不可恢复");
        }
        status = next;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTaskNo() { return taskNo; }
    public ConfigurationImportType getType() { return type; }
    public String getFileName() { return fileName; }
    public String getChecksum() { return checksum; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ImportStatus getStatus() { return status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
