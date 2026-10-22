package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

/** 本次新增：兼容返回逐行角色范围及明细、导出、配置能力。 */
public final class AdminScopeGrantResponse {
    private final String roleCode;
    private final String perspective;
    private final String scopeCode;
    private final boolean canViewDetail;
    private final boolean canExport;
    private final boolean canEditConfig;
    public AdminScopeGrantResponse(UserScope scope) {
        this.roleCode = scope.getRoleCode();
        this.perspective = scope.getPerspective().getCode();
        this.scopeCode = scope.getScopeCode();
        this.canViewDetail = scope.isCanViewDetail();
        this.canExport = scope.isCanExport();
        this.canEditConfig = scope.isCanEditConfig();
    }
    public String getRoleCode() { return roleCode; }
    public String getPerspective() { return perspective; }
    public String getScopeCode() { return scopeCode; }
    public boolean isCanViewDetail() { return canViewDetail; }
    public boolean isCanExport() { return canExport; }
    public boolean isCanEditConfig() { return canEditConfig; }
}
