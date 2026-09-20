package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PnlResult {
    private final String scopeCode;
    private final Map<String, BigDecimal> lines;

    public PnlResult(String scopeCode, Map<String, BigDecimal> lines) {
        this.scopeCode = scopeCode;
        this.lines = Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(lines));
    }

    public String getScopeCode() { return scopeCode; }
    public Map<String, BigDecimal> getLines() { return lines; }
}
