package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：用户新增/更新请求，userId 为不可变业务主键。 */
public class IamUserRequest {
    private String userId;
    private String employeeNo;
    private String username;
    private String displayName;
    private String email;
    private String department;
    private boolean enabled = true;
    private List<String> roleCodes = new ArrayList<String>();
    private List<UserScopeGrantRequest> scopes;

    public IamUser toDomain() { return new IamUser(userId, employeeNo, username, displayName, email, department, enabled); }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getRoleCodes() { return roleCodes; }
    public void setRoleCodes(List<String> roleCodes) { this.roleCodes = roleCodes; }
    public List<UserScopeGrantRequest> getScopes() { return scopes; }
    public void setScopes(List<UserScopeGrantRequest> scopes) { this.scopes = scopes; }
    public List<DataScopeSelection> toScopeSelections() {
        if (scopes == null) return null;
        List<DataScopeSelection> result = new ArrayList<DataScopeSelection>();
        for (UserScopeGrantRequest scope : scopes) result.add(required(scope).toSelection());
        return result;
    }
    private static <T> T required(T value) {
        if (value == null) throw new IllegalArgumentException("数据权限不能为空");
        return value;
    }
}
