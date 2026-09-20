package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricServiceImpl implements MetricService {
    private final MetricDefinitionRepository repository;

    public MetricServiceImpl(MetricDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public MetricDefinition upsert(String code, String name, String perspective, String unit,
                                   String formula, int order, boolean enabled, String aiTemplate,
                                   String updatedBy) {
        require(code, "metricCode");
        require(name, "metricName");
        require(formula, "formula");
        require(updatedBy, "updatedBy");
        MetricDefinition definition = repository.findByCode(code).orElse(null);
        if (definition == null) {
            definition = new MetricDefinition(null, code, name, perspective, unit, formula,
                    order, enabled, aiTemplate, updatedBy, LocalDateTime.now());
        } else {
            definition.update(name, perspective, unit, formula, order, enabled, aiTemplate, updatedBy);
        }
        return repository.save(definition);
    }

    @Override
    public List<MetricDefinition> list(String perspective, boolean includeDisabled) {
        return repository.list(perspective, includeDisabled);
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
    }
}
