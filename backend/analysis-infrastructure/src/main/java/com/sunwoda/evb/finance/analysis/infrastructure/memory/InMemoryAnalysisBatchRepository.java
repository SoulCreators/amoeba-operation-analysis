package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryAnalysisBatchRepository implements AnalysisBatchRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<String, AnalysisBatch> batches = new ConcurrentHashMap<String, AnalysisBatch>();

    @Override
    public AnalysisBatch save(AnalysisBatch batch) {
        if (batch.getId() == null) {
            AnalysisBatch assigned = new AnalysisBatch(sequence.getAndIncrement(), batch.getBatchNo(),
                    batch.getPeriod(), batch.getPerspective(), batch.getScopeCode(), batch.getStatus(),
                    batch.getCreatedBy(), batch.getCreatedAt());
            batches.put(assigned.getBatchNo(), assigned);
            return assigned;
        }
        batches.put(batch.getBatchNo(), batch);
        return batch;
    }

    @Override
    public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
        return Optional.ofNullable(batches.get(batchNo));
    }

    @Override
    public List<AnalysisBatch> findByPerspectiveAndPeriodRange(AnalysisPerspective perspective,
                                                               String fromPeriod, String toPeriod) {
        List<AnalysisBatch> matched = new ArrayList<AnalysisBatch>();
        for (AnalysisBatch batch : batches.values()) {
            if (batch.getPerspective() == perspective
                    && batch.getPeriod().compareTo(fromPeriod) >= 0
                    && batch.getPeriod().compareTo(toPeriod) <= 0) {
                matched.add(batch);
            }
        }
        matched.sort(Comparator.comparing(AnalysisBatch::getPeriod));
        return matched;
    }
}
