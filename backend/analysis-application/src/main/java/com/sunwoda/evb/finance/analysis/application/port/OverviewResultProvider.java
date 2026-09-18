package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;

import java.util.List;

public interface OverviewResultProvider {
    List<OverviewMetricResult> query(String period, AnalysisPerspective perspective, String scope);
}
