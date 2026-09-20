package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryMetricDefinitionRepository implements MetricDefinitionRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentMap<String, MetricDefinition> store =
            new ConcurrentHashMap<String, MetricDefinition>();

    public InMemoryMetricDefinitionRepository() {
        save(new MetricDefinition(null, "REVENUE", "营业收入", "ALL", "CNY",
                "SUM(REVENUE)", 10, true, "收入完成情况", "system", LocalDateTime.now()));
        save(new MetricDefinition(null, "GROSS_PROFIT", "毛利", "ALL", "CNY",
                "REVENUE-SALES_COST", 20, true, "毛利变化", "system", LocalDateTime.now()));
        save(new MetricDefinition(null, "PROFIT_RATE", "净利率", "ALL", "%",
                "NET_PROFIT/REVENUE", 30, true, "利润率变化", "system", LocalDateTime.now()));
    }

    @Override
    public MetricDefinition save(MetricDefinition definition) {
        if (definition.getId() == null) {
            MetricDefinition saved = new MetricDefinition(sequence.getAndIncrement(),
                    definition.getMetricCode(), definition.getMetricName(), definition.getApplicablePerspective(),
                    definition.getUnit(), definition.getFormulaExpression(), definition.getDisplayOrder(),
                    definition.isEnabled(), definition.getAiSummaryTemplate(), definition.getUpdatedBy(),
                    definition.getUpdatedAt());
            store.put(saved.getMetricCode(), saved);
            return saved;
        }
        store.put(definition.getMetricCode(), definition);
        return definition;
    }

    @Override
    public Optional<MetricDefinition> findByCode(String metricCode) {
        return Optional.ofNullable(store.get(metricCode));
    }

    @Override
    public List<MetricDefinition> list(String perspective, boolean includeDisabled) {
        List<MetricDefinition> result = new ArrayList<MetricDefinition>();
        for (MetricDefinition definition : store.values()) {
            boolean perspectiveMatch = perspective == null || "ALL".equalsIgnoreCase(definition.getApplicablePerspective())
                    || perspective.equalsIgnoreCase(definition.getApplicablePerspective());
            if (perspectiveMatch && (includeDisabled || definition.isEnabled())) result.add(definition);
        }
        Collections.sort(result, (left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()));
        return Collections.unmodifiableList(result);
    }
}
