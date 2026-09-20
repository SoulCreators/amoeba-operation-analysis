package com.sunwoda.evb.finance.analysis.domain.model;

public class UserScope {
    private final String userId;
    private final String roleCode;
    private final AnalysisPerspective perspective;
    private final String scopeCode;
    private final boolean canViewDetail;
    private final boolean canExport;
    private final boolean canEditConfig;
    private final boolean enabled;

    public UserScope(String userId, String roleCode, AnalysisPerspective perspective,
                     String scopeCode, boolean canViewDetail, boolean canExport,
                     boolean canEditConfig, boolean enabled) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        if (roleCode == null || roleCode.trim().isEmpty()) throw new IllegalArgumentException("角色不能为空");
        if (perspective == null) throw new IllegalArgumentException("数据视角不能为空");
        if (scopeCode == null || scopeCode.trim().isEmpty()) throw new IllegalArgumentException("授权范围不能为空");
        this.userId = userId.trim();
        this.roleCode = roleCode.trim();
        this.perspective = perspective;
        this.scopeCode = scopeCode.trim();
        this.canViewDetail = canViewDetail;
        this.canExport = canExport;
        this.canEditConfig = canEditConfig;
        this.enabled = enabled;
    }

    public String getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public String getScopeCode() { return scopeCode; }
    public boolean isCanViewDetail() { return canViewDetail; }
    public boolean isCanExport() { return canExport; }
    public boolean isCanEditConfig() { return canEditConfig; }
    public boolean isEnabled() { return enabled; }
}
