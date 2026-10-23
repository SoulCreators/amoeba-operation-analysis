package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialFactRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 非财经指标事实内存仓储，用于隔离切片与测试。 */
@Repository
public class InMemoryNonfinancialFactRepository implements NonfinancialFactRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<Long, NonfinancialFact> byId = new LinkedHashMap<Long, NonfinancialFact>();

    @Override
    public synchronized void replaceByPeriod(String period, List<NonfinancialFact> facts) {
        String required = requiredText(period, "period");
        List<Long> removed = new ArrayList<Long>();
        for (Map.Entry<Long, NonfinancialFact> entry : byId.entrySet()) {
            if (required.equals(entry.getValue().getPeriod())) {
                removed.add(entry.getKey());
            }
        }
        for (Long id : removed) {
            byId.remove(id);
        }
        for (NonfinancialFact fact : facts) {
            NonfinancialFact assigned = new NonfinancialFact(sequence.getAndIncrement(),
                    fact.getPeriod(), fact.getBaseCode(), fact.getProductionLine(),
                    fact.getCellModel(), fact.getQualityCategory(), fact.getTargetActual(),
                    fact.getQualityValue(), fact.getSourceRowNo(), fact.getEffectiveFileVersion());
            byId.put(assigned.getId(), assigned);
        }
    }

    @Override
    public synchronized List<NonfinancialFact> findByPeriod(String period) {
        String required = requiredText(period, "period");
        List<NonfinancialFact> result = new ArrayList<NonfinancialFact>();
        for (NonfinancialFact fact : byId.values()) {
            if (required.equals(fact.getPeriod())) {
                result.add(fact);
            }
        }
        return result;
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
