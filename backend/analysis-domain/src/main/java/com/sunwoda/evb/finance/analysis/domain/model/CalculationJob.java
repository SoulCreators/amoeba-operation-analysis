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
    private CalculationStage stage;
    private LocalDateTime heartbeatAt;
    private String errorCode;
    private String traceId;
    private long processedRows;
    private int issueCount;

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

    /**
     * 从持久化记录恢复计算作业的完整领域状态。
     *
     * <p>本次修改仅提供 JDBC 适配器所需的状态恢复入口，不新增重试、调度或幂等业务规则。</p>
     */
    public static CalculationJob restore(String jobId, String requestKey, String batchNo,
                                         LocalDateTime createdAt, CalculationJobStatus status,
                                         int attemptNo, String lastError, LocalDateTime startedAt,
                                         LocalDateTime finishedAt) {
        return restore(jobId, requestKey, batchNo, createdAt, status, attemptNo, lastError,
                startedAt, finishedAt, null, null, null, null, 0L, 0);
    }

    /**
     * 从持久化记录恢复含阶段进度与心跳字段的完整领域状态。
     *
     * <p>本次新增：为阶段日志与心跳补充当前阶段、心跳时间、错误码、追踪号和累计处理/异常计数。</p>
     */
    public static CalculationJob restore(String jobId, String requestKey, String batchNo,
                                         LocalDateTime createdAt, CalculationJobStatus status,
                                         int attemptNo, String lastError, LocalDateTime startedAt,
                                         LocalDateTime finishedAt, CalculationStage stage,
                                         LocalDateTime heartbeatAt, String errorCode, String traceId,
                                         long processedRows, int issueCount) {
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo 必须大于等于 1");
        }
        if (processedRows < 0) {
            throw new IllegalArgumentException("processedRows 不能为负");
        }
        if (issueCount < 0) {
            throw new IllegalArgumentException("issueCount 不能为负");
        }
        return new CalculationJob(jobId, requestKey, batchNo, createdAt, status, attemptNo,
                lastError, startedAt, finishedAt, stage, heartbeatAt, errorCode, traceId,
                processedRows, issueCount);
    }

    private CalculationJob(String jobId, String requestKey, String batchNo, LocalDateTime createdAt,
                            CalculationJobStatus status, int attemptNo, String lastError,
                            LocalDateTime startedAt, LocalDateTime finishedAt) {
        this(jobId, requestKey, batchNo, createdAt, status, attemptNo, lastError, startedAt,
                finishedAt, null, null, null, null, 0L, 0);
    }

    private CalculationJob(String jobId, String requestKey, String batchNo, LocalDateTime createdAt,
                            CalculationJobStatus status, int attemptNo, String lastError,
                            LocalDateTime startedAt, LocalDateTime finishedAt,
                            CalculationStage stage, LocalDateTime heartbeatAt, String errorCode,
                            String traceId, long processedRows, int issueCount) {
        this.jobId = requiredText(jobId, "jobId");
        this.requestKey = requiredText(requestKey, "requestKey");
        this.batchNo = requiredText(batchNo, "batchNo");
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt 不能为空");
        }
        this.createdAt = createdAt;
        this.status = status;
        this.attemptNo = attemptNo;
        this.lastError = lastError;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.stage = stage;
        this.heartbeatAt = heartbeatAt;
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.processedRows = processedRows;
        this.issueCount = issueCount;
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

    /**
     * 将当前运行中的作业标记为失败，同时保留机器可读错误码。
     *
     * <p>本次新增：错误码与可读原因分别持久化，供失败任务独立追溯阶段与错误分类。</p>
     */
    public void fail(String errorCode, String error) {
        this.errorCode = requiredText(errorCode, "errorCode");
        fail(error);
    }

    /**
     * 记录阶段进度与心跳，更新当前阶段、累计处理行数、异常数和心跳时间。
     *
     * <p>本次新增：心跳不改变作业生命周期状态，仅用于运维观测和超时判活。</p>
     */
    public void recordProgress(CalculationStage stage, long processedRows, int issueCount) {
        if (stage == null) {
            throw new IllegalArgumentException("stage 不能为空");
        }
        if (processedRows < 0) {
            throw new IllegalArgumentException("processedRows 不能为负");
        }
        if (issueCount < 0) {
            throw new IllegalArgumentException("issueCount 不能为负");
        }
        this.stage = stage;
        this.processedRows = processedRows;
        this.issueCount = issueCount;
        this.heartbeatAt = LocalDateTime.now();
    }

    /**
     * 记录阶段失败，仅更新当前阶段、错误码与心跳，不改变作业整体生命周期状态。
     */
    public void recordStageFailure(CalculationStage stage, String errorCode) {
        if (stage == null) {
            throw new IllegalArgumentException("stage 不能为空");
        }
        this.stage = stage;
        this.errorCode = requiredText(errorCode, "errorCode");
        this.heartbeatAt = LocalDateTime.now();
    }

    /**
     * 记录作业追踪号，供跨链路统一 traceId。
     */
    public void attachTraceId(String traceId) {
        this.traceId = requiredText(traceId, "traceId");
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

    public CalculationStage getStage() {
        return stage;
    }

    public LocalDateTime getHeartbeatAt() {
        return heartbeatAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public long getProcessedRows() {
        return processedRows;
    }

    public int getIssueCount() {
        return issueCount;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
