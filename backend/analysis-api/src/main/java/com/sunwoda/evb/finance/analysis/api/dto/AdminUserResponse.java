package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserAuthorization;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本次新增：匹配前端 User 合同，并附加 scopeGrants 保留布尔能力明细。 */
public final class AdminUserResponse {
    private final String userId;
    private final String employeeNo;
    private final String username;
    private final String displayName;
    private final String email;
    private final List<String> roleCodes;
    private final List<AdminDataScopeResponse> scopes;
    private final List<AdminScopeGrantResponse> scopeGrants;
    private final boolean enabled;

    public AdminUserResponse(IamUser user, UserAuthorization authorization) {
        this.userId = user.getUserId();
        this.employeeNo = user.getEmployeeNo();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.email = user.getEmail();
        this.roleCodes = authorization.getRoleCodes();
        this.enabled = user.isEnabled();
        Map<String, List<String>> grouped = new LinkedHashMap<String, List<String>>();
        List<AdminScopeGrantResponse> grants = new ArrayList<AdminScopeGrantResponse>();
        for (UserScope scope : authorization.getScopes()) {
            String perspective = scope.getPerspective().getCode();
            List<String> codes = grouped.get(perspective);
            if (codes == null) { codes = new ArrayList<String>(); grouped.put(perspective, codes); }
            if (!codes.contains(scope.getScopeCode())) codes.add(scope.getScopeCode());
            grants.add(new AdminScopeGrantResponse(scope));
        }
        List<AdminDataScopeResponse> selections = new ArrayList<AdminDataScopeResponse>();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            selections.add(new AdminDataScopeResponse(entry.getKey(), entry.getValue()));
        }
        this.scopes = Collections.unmodifiableList(selections);
        this.scopeGrants = Collections.unmodifiableList(grants);
    }
    public String getUserId() { return userId; }
    public String getEmployeeNo() { return employeeNo; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public List<String> getRoleCodes() { return roleCodes; }
    public List<AdminDataScopeResponse> getScopes() { return scopes; }
    public List<AdminScopeGrantResponse> getScopeGrants() { return scopeGrants; }
    public boolean isEnabled() { return enabled; }
}
