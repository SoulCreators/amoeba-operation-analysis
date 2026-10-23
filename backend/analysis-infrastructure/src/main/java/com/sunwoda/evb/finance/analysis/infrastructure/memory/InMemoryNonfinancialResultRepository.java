package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialResultRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 非财经指标配对结果内存仓储，用于隔离切片与测试。 */
@Repository
public class InMemoryNonfinancialResultRepository implements NonfinancialResultRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final Map<Long, NonfinancialResult> byId = new LinkedHashMap<Long, NonfinancialResult>();

    @Override
    public synchronized void replaceByPeriod(String period, List<NonfinancialResult> results) {
        String required = requiredText(period, "period");
        List<Long> removed = new ArrayList<Long>();
        for (Map.Entry<Long, NonfinancialResult> entry : byId.entrySet()) {
            if (required.equals(entry.getValue().getPeriod())) {
                removed.add(entry.getKey());
            }
        }
        for (Long id : removed) {
            byId.remove(id);
        }
        for (NonfinancialResult result : results) {
            NonfinancialResult assigned = new NonfinancialResult(sequence.getAndIncrement(),
                    result.getPeriod(), result.getBaseCode(), result.getProductionLine(),
                    result.getCellModel(), result.getQualityCategory(), result.getTargetValue(),
                    result.getActualValue(), result.getGap(), result.getAchievementRate(),
                    result.getPairingStatus(), result.getLeaderEmployeeNo(),
                    result.isLeaderConflict());
            byId.put(assigned.getId(), assigned);
        }
    }

    @Override
    public synchronized List<NonfinancialResult> findByPeriod(String period) {
        String required = requiredText(period, "period");
        List<NonfinancialResult> result = new ArrayList<NonfinancialResult>();
        for (NonfinancialResult item : byId.values()) {
            if (required.equals(item.getPeriod())) {
                result.add(item);
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
