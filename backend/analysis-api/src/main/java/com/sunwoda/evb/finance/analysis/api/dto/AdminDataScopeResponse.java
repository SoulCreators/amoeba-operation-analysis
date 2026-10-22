package com.sunwoda.evb.finance.analysis.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：匹配前端的按视角聚合数据范围响应。 */
public final class AdminDataScopeResponse {
    private final String perspective;
    private final List<String> scopeCodes;
    public AdminDataScopeResponse(String perspective, List<String> scopeCodes) {
        this.perspective = perspective;
        this.scopeCodes = Collections.unmodifiableList(new ArrayList<String>(scopeCodes));
    }
    public String getPerspective() { return perspective; }
    public List<String> getScopeCodes() { return scopeCodes; }
}
