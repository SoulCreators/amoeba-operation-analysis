package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计算作业单个阶段的进度与失败日志。
 *
 * <p>阶段日志是独立的失败任务追溯载体：每个阶段记录开始/结束时间、处理行数、金额控制数、
 * 异常数、错误码与追踪号；阶段失败只标记该阶段，不把部分计算结果暴露为正式金额。</p>
 *
 * <p>值对象不可变，阶段推进通过 {@link #succeed} / {@link #fail} 生成新实例；持久化按
 * (jobId, stage) 幂等覆盖，保证同一作业同一阶段只有一条进度记录。</p>
 */
public final class CalculationStageLog {
    private final Long id;
    private final String jobId;
    private final CalculationStage stage;
    private final CalculationStageStatus status;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final long processedRows;
    private final BigDecimal controlAmount;
    private final int issueCount;
    private final String errorCode;
    private final String traceId;
    private final String message;

    private CalculationStageLog(Long id, String jobId, CalculationStage stage,
                                CalculationStageStatus status, LocalDateTime startedAt,
                                LocalDateTime finishedAt, long processedRows,
                                BigDecimal controlAmount, int issueCount, String errorCode,
                                String traceId, String message) {
        this.id = id;
        this.jobId = requiredText(jobId, "jobId");
        this.stage = requireNonNull(stage, "stage");
        this.status = requireNonNull(status, "status");
        this.startedAt = requireNonNull(startedAt, "startedAt");
        if (processedRows < 0) {
            throw new IllegalArgumentException("processedRows 不能为负");
        }
        if (issueCount < 0) {
            throw new IllegalArgumentException("issueCount 不能为负");
        }
        this.finishedAt = finishedAt;
        this.processedRows = processedRows;
        this.controlAmount = controlAmount;
        this.issueCount = issueCount;
        this.errorCode = errorCode;
        this.traceId = traceId;
        this.message = message;
    }

    /**
     * 记录阶段开始，进入 {@link CalculationStageStatus#RUNNING}。
     */
    public static CalculationStageLog start(String jobId, CalculationStage stage, String traceId) {
        return new CalculationStageLog(null, jobId, stage, CalculationStageStatus.RUNNING,
                LocalDateTime.now(), null, 0L, null, 0, null, traceId, null);
    }

    /**
     * 从持久化记录恢复阶段日志。
     */
    public static CalculationStageLog restore(Long id, String jobId, CalculationStage stage,
                                              CalculationStageStatus status, LocalDateTime startedAt,
                                              LocalDateTime finishedAt, long processedRows,
                                              BigDecimal controlAmount, int issueCount,
                                              String errorCode, String traceId, String message) {
        return new CalculationStageLog(id, jobId, stage, status, startedAt, finishedAt,
                processedRows, controlAmount, issueCount, errorCode, traceId, message);
    }

    /**
     * 阶段成功，返回携带终态指标的新实例。
     */
    public CalculationStageLog succeed(long processedRows, BigDecimal controlAmount, int issueCount) {
        return new CalculationStageLog(id, jobId, stage, CalculationStageStatus.SUCCEEDED,
                startedAt, LocalDateTime.now(), processedRows, controlAmount, issueCount,
                null, traceId, null);
    }

    /**
     * 阶段失败，返回携带错误码与失败说明的新实例。
     */
    public CalculationStageLog fail(String errorCode, String message) {
        return new CalculationStageLog(id, jobId, stage, CalculationStageStatus.FAILED,
                startedAt, LocalDateTime.now(), processedRows, controlAmount, issueCount,
                requiredText(errorCode, "errorCode"), traceId, message);
    }

    public Long getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public CalculationStage getStage() {
        return stage;
    }

    public CalculationStageStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public long getProcessedRows() {
        return processedRows;
    }

    public BigDecimal getControlAmount() {
        return controlAmount;
    }

    public int getIssueCount() {
        return issueCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getMessage() {
        return message;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
