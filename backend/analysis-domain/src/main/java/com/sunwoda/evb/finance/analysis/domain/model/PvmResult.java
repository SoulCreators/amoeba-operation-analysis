package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PvmResult {
    private final String scopeCode;
    private final String logicCode;
    private final Map<String, BigDecimal> values;

    public PvmResult(String scopeCode, String logicCode, Map<String, BigDecimal> values) {
        this.scopeCode = scopeCode;
        this.logicCode = logicCode;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(values));
    }

    public String getScopeCode() { return scopeCode; }
    public String getLogicCode() { return logicCode; }
    public Map<String, BigDecimal> getValues() { return values; }
}
