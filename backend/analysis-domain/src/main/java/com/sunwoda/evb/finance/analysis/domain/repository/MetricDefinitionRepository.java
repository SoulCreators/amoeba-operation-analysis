package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;

import java.util.List;
import java.util.Optional;

public interface MetricDefinitionRepository {
    MetricDefinition save(MetricDefinition definition);
    Optional<MetricDefinition> findByCode(String metricCode);
    List<MetricDefinition> list(String perspective, boolean includeDisabled);
}
