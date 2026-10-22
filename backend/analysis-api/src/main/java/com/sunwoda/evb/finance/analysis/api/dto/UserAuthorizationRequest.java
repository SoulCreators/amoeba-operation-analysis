package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：一次原子提交用户角色和数据权限。 */
public class UserAuthorizationRequest {
    private List<String> roleCodes = new ArrayList<String>();
    private List<UserScopeGrantRequest> scopes = new ArrayList<UserScopeGrantRequest>();

    public List<UserScope> toScopes(String userId) {
        if (scopes == null) return Collections.emptyList();
        List<UserScope> result = new ArrayList<UserScope>();
        for (UserScopeGrantRequest scope : scopes) {
            if (scope == null) throw new IllegalArgumentException("数据权限不能为空");
            result.add(scope.toDomain(userId));
        }
        return result;
    }
    public boolean hasDetailedGrants() {
        if (scopes == null || scopes.isEmpty()) return false;
        for (UserScopeGrantRequest scope : scopes) if (!scope.isDetailedGrant()) return false;
        return true;
    }
    public List<DataScopeSelection> toScopeSelections() {
        if (scopes == null) return Collections.emptyList();
        List<DataScopeSelection> result = new ArrayList<DataScopeSelection>();
        for (UserScopeGrantRequest scope : scopes) {
            if (scope == null) throw new IllegalArgumentException("数据权限不能为空");
            result.add(scope.toSelection());
        }
        return result;
    }
    public List<String> getRoleCodes() { return roleCodes; }
    public void setRoleCodes(List<String> roleCodes) { this.roleCodes = roleCodes; }
    public List<UserScopeGrantRequest> getScopes() { return scopes; }
    public void setScopes(List<UserScopeGrantRequest> scopes) { this.scopes = scopes; }
}
