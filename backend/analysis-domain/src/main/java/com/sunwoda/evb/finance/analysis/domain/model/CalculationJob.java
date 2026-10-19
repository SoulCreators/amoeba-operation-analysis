package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算作业领域模型。
 *
 * <p>本阶段只描述作业生命周期，不负责持久化、调度或调用计算服务。</p>
 */
public final class CalculationJob {
    private final String jobId;
    private final String requestKey;
    private final String batchNo;
    private final LocalDateTime createdAt;
    private CalculationJobStatus status;
    private int attemptNo;
    private String lastError;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public CalculationJob(String jobId, String requestKey, String batchNo, LocalDateTime createdAt) {
        this.jobId = requiredText(jobId, "jobId");
        this.requestKey = requiredText(requestKey, "requestKey");
        this.batchNo = requiredText(batchNo, "batchNo");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为空");
        }
        this.createdAt = createdAt;
        this.status = CalculationJobStatus.QUEUED;
        this.attemptNo = 1;
    }

    public void moveTo(CalculationJobStatus next) {
        CalculationJobLifecyclePolicy.validate(status, next);
        if (status == CalculationJobStatus.FAILED && next == CalculationJobStatus.QUEUED) {
            attemptNo++;
            startedAt = null;
            finishedAt = null;
        }
        if (next == CalculationJobStatus.RUNNING) {
            startedAt = LocalDateTime.now();
        }
        if (next == CalculationJobStatus.SUCCEEDED || next == CalculationJobStatus.FAILED) {
            finishedAt = LocalDateTime.now();
        }
        if (next == CalculationJobStatus.SUCCEEDED) {
            lastError = null;
        }
        status = next;
    }

    /**
     * 将当前运行中的作业标记为失败并保留失败原因。
     */
    public void fail(String error) {
        String requiredError = requiredText(error, "lastError");
        moveTo(CalculationJobStatus.FAILED);
        lastError = requiredError;
    }

    public String getJobId() {
        return jobId;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public CalculationJobStatus getStatus() {
        return status;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
