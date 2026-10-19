package com.sunwoda.evb.finance.analysis.application.model;

import java.time.LocalDateTime;

/**
 * 导出任务的应用层视图。
 *
 * <p>文件内容不放在任务对象中；完成后的文件由文件存储端口按任务号关联。</p>
 */
public final class ExportJob {
    private final String jobId;
    private final String userId;
    private final String requestKey;
    private final String fileName;
    private final String contentType;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime finishedAt;
    private final LocalDateTime expiresAt;
    private final ExportJobStatus status;
    private final String errorMessage;

    private ExportJob(String jobId, String userId, String requestKey,
                      String fileName, String contentType,
                      LocalDateTime createdAt, LocalDateTime updatedAt,
                      LocalDateTime finishedAt, LocalDateTime expiresAt,
                      ExportJobStatus status,
                      String errorMessage) {
        this.jobId = required(jobId, "jobId");
        this.userId = required(userId, "userId");
        this.requestKey = required(requestKey, "requestKey");
        this.fileName = required(fileName, "fileName");
        this.contentType = required(contentType, "contentType");
        if (createdAt == null) throw new IllegalArgumentException("createdAt 不能为空");
        if (updatedAt == null) throw new IllegalArgumentException("updatedAt 不能为空");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt 不能为空");
        if (expiresAt.isBefore(createdAt)) throw new IllegalArgumentException("expiresAt 不能早于 createdAt");
        if (status == null) throw new IllegalArgumentException("status 不能为空");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.finishedAt = finishedAt;
        this.expiresAt = expiresAt;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public static ExportJob queued(String jobId, String userId, String requestKey,
                                   String fileName, String contentType,
                                   LocalDateTime createdAt) {
        return new ExportJob(jobId, userId, requestKey, fileName, contentType,
                createdAt, createdAt, null, createdAt.plusMinutes(15),
                ExportJobStatus.QUEUED, null);
    }

    public ExportJob running(LocalDateTime at) {
        return copy(ExportJobStatus.RUNNING, null, at, null);
    }

    public ExportJob succeeded(String completedFileName, String completedContentType,
                               LocalDateTime at) {
        return new ExportJob(jobId, userId, requestKey,
                completedFileName, completedContentType, createdAt, at, at, expiresAt,
                ExportJobStatus.SUCCEEDED, null);
    }

    public ExportJob failed(String message, LocalDateTime at) {
        return copy(ExportJobStatus.FAILED, required(message, "errorMessage"), at, at);
    }

    private ExportJob copy(ExportJobStatus nextStatus, String nextError,
                           LocalDateTime nextUpdatedAt, LocalDateTime nextFinishedAt) {
        return new ExportJob(jobId, userId, requestKey, fileName, contentType,
                createdAt, nextUpdatedAt, nextFinishedAt, expiresAt, nextStatus, nextError);
    }

    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getRequestKey() { return requestKey; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public ExportJobStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isExpired(LocalDateTime now) {
        return now != null && !now.isBefore(expiresAt)
                && status != ExportJobStatus.EXPIRED;
    }

    public ExportJob expired(LocalDateTime at) {
        if (status == ExportJobStatus.EXPIRED) return this;
        return new ExportJob(jobId, userId, requestKey, fileName, contentType,
                createdAt, at, finishedAt, expiresAt, ExportJobStatus.EXPIRED, errorMessage);
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
