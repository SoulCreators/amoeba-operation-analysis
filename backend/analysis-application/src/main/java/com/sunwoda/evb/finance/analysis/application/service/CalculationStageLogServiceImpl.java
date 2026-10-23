package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationJobDetail;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationStageLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link CalculationStageLogService} 默认实现。
 *
 * <p>阶段日志按 (jobId, stage) 幂等，阶段推进复用已存在日志的开始时与追踪号；
 * 作业整体生命周期状态由既有计算编排管理，本服务只更新进度/心跳/错误码字段。</p>
 */
@Service
public class CalculationStageLogServiceImpl implements CalculationStageLogService {
    private final CalculationJobRepository jobRepository;
    private final CalculationStageLogRepository stageLogRepository;

    public CalculationStageLogServiceImpl(CalculationJobRepository jobRepository,
                                          CalculationStageLogRepository stageLogRepository) {
        if (jobRepository == null) {
            throw new IllegalArgumentException("jobRepository 不能为空");
        }
        if (stageLogRepository == null) {
            throw new IllegalArgumentException("stageLogRepository 不能为空");
        }
        this.jobRepository = jobRepository;
        this.stageLogRepository = stageLogRepository;
    }

    @Override
    @Transactional
    public void recordStageStarted(String jobId, CalculationStage stage, String traceId) {
        CalculationJob job = requireJob(jobId);
        stageLogRepository.save(CalculationStageLog.start(jobId, stage, traceId));
        job.recordProgress(stage, 0L, 0);
        if (traceId != null && !traceId.trim().isEmpty()) {
            job.attachTraceId(traceId);
        }
        jobRepository.save(job);
    }

    @Override
    @Transactional
    public void recordHeartbeat(String jobId, CalculationStage stage, long processedRows, int issueCount) {
        CalculationJob job = requireJob(jobId);
        job.recordProgress(stage, processedRows, issueCount);
        jobRepository.save(job);
    }

    @Override
    @Transactional
    public void recordStageSucceeded(String jobId, CalculationStage stage, long processedRows,
                                     BigDecimal controlAmount, int issueCount) {
        CalculationJob job = requireJob(jobId);
        CalculationStageLog current = findStageLog(jobId, stage);
        CalculationStageLog base = current != null ? current : CalculationStageLog.start(jobId, stage, null);
        stageLogRepository.save(base.succeed(processedRows, controlAmount, issueCount));
        job.recordProgress(stage, processedRows, issueCount);
        jobRepository.save(job);
    }

    @Override
    @Transactional
    public void recordStageFailed(String jobId, CalculationStage stage, String errorCode, String message) {
        CalculationJob job = requireJob(jobId);
        CalculationStageLog current = findStageLog(jobId, stage);
        CalculationStageLog base = current != null ? current : CalculationStageLog.start(jobId, stage, null);
        stageLogRepository.save(base.fail(errorCode, message));
        job.recordStageFailure(stage, errorCode);
        jobRepository.save(job);
    }

    @Override
    public CalculationJobDetail getJobDetail(String jobId) {
        CalculationJob job = requireJob(jobId);
        List<CalculationStageLog> stageLogs = stageLogRepository.findByJobId(jobId);
        return new CalculationJobDetail(job, stageLogs, job.getStatus() == CalculationJobStatus.FAILED);
    }

    private CalculationJob requireJob(String jobId) {
        return jobRepository.findByJobId(requiredText(jobId, "jobId"))
                .orElseThrow(() -> new IllegalArgumentException("计算作业不存在: " + jobId));
    }

    private CalculationStageLog findStageLog(String jobId, CalculationStage stage) {
        for (CalculationStageLog log : stageLogRepository.findByJobId(jobId)) {
            if (log.getStage() == stage) {
                return log;
            }
        }
        return null;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
