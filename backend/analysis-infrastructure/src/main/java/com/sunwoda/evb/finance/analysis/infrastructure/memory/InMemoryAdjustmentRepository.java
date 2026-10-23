package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.repository.AdjustmentRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryAdjustmentRepository implements AdjustmentRepository {
    private final ConcurrentMap<String, Adjustment> store = new ConcurrentHashMap<String, Adjustment>();

    @Override
    public Adjustment save(Adjustment adjustment) {
        store.put(adjustment.getAdjustmentId(), adjustment);
        return adjustment;
    }

    @Override
    public Optional<Adjustment> findById(String adjustmentId) {
        return Optional.ofNullable(store.get(adjustmentId));
    }

    @Override
    public List<Adjustment> findByPeriod(String period) {
        List<Adjustment> result = new ArrayList<Adjustment>();
        for (Adjustment adjustment : store.values()) {
            if (adjustment.getPeriod().equals(period)) result.add(adjustment);
        }
        return result;
    }

    @Override
    public List<Adjustment> findActive(String period, AnalysisPerspective perspective) {
        List<Adjustment> result = new ArrayList<Adjustment>();
        for (Adjustment adjustment : store.values()) {
            if (adjustment.isActive() && adjustment.getPeriod().equals(period)
                    && adjustment.getPerspective() == perspective) {
                result.add(adjustment);
            }
        }
        return result;
    }
}
