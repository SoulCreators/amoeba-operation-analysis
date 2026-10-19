package com.sunwoda.evb.finance.analysis.application.model;

import java.time.LocalDateTime;

/** A claimed, expired export file awaiting internal cleanup. */
public final class ExportCleanupTask {
    private final String jobId;
    private final String userId;
    private final String fileKey;
    private final int attempts;
    private final String claimToken;
    private final LocalDateTime expiresAt;

    public ExportCleanupTask(String jobId, String userId, String fileKey,
                             int attempts, String claimToken,
                             LocalDateTime expiresAt) {
        this.jobId = required(jobId, "jobId");
        this.userId = required(userId, "userId");
        this.fileKey = required(fileKey, "fileKey");
        if (attempts < 0) throw new IllegalArgumentException("attempts 不能小于0");
        this.attempts = attempts;
        this.claimToken = required(claimToken, "claimToken");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt 不能为空");
        this.expiresAt = expiresAt;
    }

    public String getJobId() { return jobId; }
    public String getUserId() { return userId; }
    public String getFileKey() { return fileKey; }
    public int getAttempts() { return attempts; }
    public String getClaimToken() { return claimToken; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
