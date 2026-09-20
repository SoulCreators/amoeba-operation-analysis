package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.MetricResult;

import java.util.List;

public interface MetricTreeService {
    List<MetricResult> calculate(String batchNo, String userId);
}
