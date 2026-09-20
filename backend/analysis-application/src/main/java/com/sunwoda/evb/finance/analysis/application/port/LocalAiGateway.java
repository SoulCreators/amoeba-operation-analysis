package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;

import java.util.Map;

public interface LocalAiGateway {
    Map<String, String> generate(CalculationResult result);
}
