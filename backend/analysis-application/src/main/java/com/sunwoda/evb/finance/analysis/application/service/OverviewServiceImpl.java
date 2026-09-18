package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.OverviewResultProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OverviewServiceImpl implements OverviewService {
    private final OverviewResultProvider provider;

    public OverviewServiceImpl(OverviewResultProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<OverviewMetricResult> query(String period, AnalysisPerspective perspective, String scope) {
        if (period == null || perspective == null) {
            throw new IllegalArgumentException("period和perspective不能为空");
        }
        return provider.query(period, perspective, scope == null || scope.trim().isEmpty() ? "ALL" : scope);
    }
}
