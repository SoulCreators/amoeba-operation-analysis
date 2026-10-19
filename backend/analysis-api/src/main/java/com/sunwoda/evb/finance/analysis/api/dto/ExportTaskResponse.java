package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.application.model.ExportJob;

import java.time.LocalDateTime;

/** API representation of an export task; it deliberately contains no file content. */
public class ExportTaskResponse {
    private String taskId;
    private String status;
    private String fileName;
    private String contentType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiresAt;
    private String errorMessage;

    public static ExportTaskResponse from(ExportJob job) {
        ExportTaskResponse response = new ExportTaskResponse();
        response.taskId = job.getJobId();
        response.status = job.getStatus().name();
        response.fileName = job.getFileName();
        response.contentType = job.getContentType();
        response.createdAt = job.getCreatedAt();
        response.updatedAt = job.getUpdatedAt();
        response.finishedAt = job.getFinishedAt();
        response.expiresAt = job.getExpiresAt();
        response.errorMessage = job.getErrorMessage();
        return response;
    }

    public static ExportTaskResponse accepted(String taskId) {
        ExportTaskResponse response = new ExportTaskResponse();
        response.taskId = taskId;
        response.status = "QUEUED";
        return response;
    }

    public String getTaskId() { return taskId; }
    public String getStatus() { return status; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getErrorMessage() { return errorMessage; }
}
