package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 本次新增：前端按视角聚合提交的数据范围；ALL 视角由应用层展开。 */
public final class DataScopeSelection {
    private final String perspective;
    private final List<String> scopeCodes;

    public DataScopeSelection(String perspective, List<String> scopeCodes) {
        if (perspective == null || perspective.trim().isEmpty()) throw new IllegalArgumentException("数据视角不能为空");
        this.perspective = perspective.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(this.perspective)) AnalysisPerspective.fromCode(this.perspective);
        if (scopeCodes == null || scopeCodes.isEmpty()) throw new IllegalArgumentException("授权范围不能为空");
        List<String> normalized = new ArrayList<String>();
        for (String scopeCode : scopeCodes) {
            if (scopeCode == null || scopeCode.trim().isEmpty()) throw new IllegalArgumentException("授权范围不能为空");
            String code = scopeCode.trim().toUpperCase(Locale.ROOT);
            if (!normalized.contains(code)) normalized.add(code);
        }
        this.scopeCodes = Collections.unmodifiableList(normalized);
    }

    public String getPerspective() { return perspective; }
    public List<String> getScopeCodes() { return scopeCodes; }
}
