package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BatchServiceImpl implements BatchService {
    private final AnalysisBatchRepository repository;
    private final BatchReadinessService readinessService;

    public BatchServiceImpl(AnalysisBatchRepository repository, BatchReadinessService readinessService) {
        this.repository = repository;
        this.readinessService = readinessService;
    }

    @Override
    public AnalysisBatch create(String batchNo, String period, AnalysisPerspective perspective,
                                String scopeCode, String createdBy) {
        if (isBlank(batchNo) || isBlank(period) || perspective == null || isBlank(createdBy)) {
            throw new IllegalArgumentException("batchNo、period、perspective、createdBy均不能为空");
        }
        if (!period.matches("20\\d{2}-((0[1-9])|(1[0-2]))")) {
            throw new IllegalArgumentException("period必须为YYYY-MM格式");
        }
        if (repository.findByBatchNo(batchNo).isPresent()) {
            throw new IllegalStateException("批次号已存在: " + batchNo);
        }
        return repository.save(new AnalysisBatch(null, batchNo, period, perspective, scopeCode,
                BatchStatus.DRAFT, createdBy, LocalDateTime.now()));
    }

    @Override
    public AnalysisBatch get(String batchNo) {
        return repository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
    }

    @Override
    public AnalysisBatch move(String batchNo, BatchStatus status) {
        AnalysisBatch batch = get(batchNo);
        ensureReadyForCalculation(batch, status);
        batch.moveTo(status);
        return repository.save(batch);
    }

    private void ensureReadyForCalculation(AnalysisBatch batch, BatchStatus nextStatus) {
        if (batch.getStatus() != BatchStatus.CHECKING
                || nextStatus != BatchStatus.READY_FOR_CALCULATION) {
            return;
        }
        BatchReadiness readiness = readinessService.get(batch.getBatchNo());
        if (readiness == null || !readiness.isReadyForCalculation()) {
            throw new IllegalStateException("批次完整性未通过，无法进入 READY_FOR_CALCULATION: "
                    + batch.getBatchNo());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
