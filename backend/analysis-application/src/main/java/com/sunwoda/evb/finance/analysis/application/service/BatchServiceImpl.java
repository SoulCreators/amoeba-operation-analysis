package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BatchServiceImpl implements BatchService {
    private final AnalysisBatchRepository repository;

    public BatchServiceImpl(AnalysisBatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public AnalysisBatch create(String batchNo, String period, AnalysisPerspective perspective, String createdBy) {
        if (isBlank(batchNo) || isBlank(period) || perspective == null || isBlank(createdBy)) {
            throw new IllegalArgumentException("batchNo、period、perspective、createdBy均不能为空");
        }
        if (!period.matches("20\\d{2}-((0[1-9])|(1[0-2]))")) {
            throw new IllegalArgumentException("period必须为YYYY-MM格式");
        }
        if (repository.findByBatchNo(batchNo).isPresent()) {
            throw new IllegalStateException("批次号已存在: " + batchNo);
        }
        return repository.save(new AnalysisBatch(null, batchNo, period, perspective,
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
        batch.moveTo(status);
        return repository.save(batch);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
