package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;

import java.util.List;

public interface OverviewService {
    List<OverviewMetricResult> query(String period, AnalysisPerspective perspective, String scope);
}
