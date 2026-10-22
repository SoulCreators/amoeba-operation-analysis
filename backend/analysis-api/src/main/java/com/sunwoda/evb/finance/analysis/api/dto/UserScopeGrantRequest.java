package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.Collections;
import java.util.List;

/** 本次新增：一个角色在一个经营视角与范围上的布尔能力授权。 */
public class UserScopeGrantRequest {
    private String roleCode;
    private String perspective;
    private String scopeCode;
    private List<String> scopeCodes;
    private Boolean canViewDetail;
    private Boolean canExport;
    private Boolean canEditConfig;

    public UserScope toDomain(String userId) {
        return new UserScope(userId, roleCode, AnalysisPerspective.fromCode(perspective), scopeCode,
                Boolean.TRUE.equals(canViewDetail), Boolean.TRUE.equals(canExport),
                Boolean.TRUE.equals(canEditConfig), true);
    }
    public DataScopeSelection toSelection() {
        List<String> codes = scopeCodes == null ? Collections.singletonList(scopeCode) : scopeCodes;
        return new DataScopeSelection(perspective, codes);
    }
    public boolean isDetailedGrant() { return roleCode != null && scopeCode != null; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getPerspective() { return perspective; }
    public void setPerspective(String perspective) { this.perspective = perspective; }
    public String getScopeCode() { return scopeCode; }
    public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }
    public List<String> getScopeCodes() { return scopeCodes; }
    public void setScopeCodes(List<String> scopeCodes) { this.scopeCodes = scopeCodes; }
    public Boolean getCanViewDetail() { return canViewDetail; }
    public void setCanViewDetail(Boolean canViewDetail) { this.canViewDetail = canViewDetail; }
    public Boolean getCanExport() { return canExport; }
    public void setCanExport(Boolean canExport) { this.canExport = canExport; }
    public Boolean getCanEditConfig() { return canEditConfig; }
    public void setCanEditConfig(Boolean canEditConfig) { this.canEditConfig = canEditConfig; }
}
