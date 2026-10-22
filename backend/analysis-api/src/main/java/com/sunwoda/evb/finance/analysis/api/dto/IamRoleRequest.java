package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamRole;

import java.util.ArrayList;
import java.util.List;

/** 本次新增：角色新增/更新请求。 */
public class IamRoleRequest {
    private String roleCode;
    private String roleName;
    private String description;
    private boolean systemRole;
    private boolean enabled = true;
    private List<String> menuCodes = new ArrayList<String>();

    public IamRole toDomain() { return new IamRole(roleCode, roleName, description, systemRole, enabled); }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getMenuCodes() { return menuCodes; }
    public void setMenuCodes(List<String> menuCodes) { this.menuCodes = menuCodes; }
}
