package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;

import java.util.List;

public interface MetricService {
    MetricDefinition upsert(String code, String name, String perspective, String unit,
                            String formula, int order, boolean enabled, String aiTemplate,
                            String updatedBy);
    List<MetricDefinition> list(String perspective, boolean includeDisabled);
}
