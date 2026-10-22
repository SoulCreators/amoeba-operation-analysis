package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：匹配前端 Role 合同。 */
public final class AdminRoleResponse {
    private final String roleCode;
    private final String roleName;
    private final String description;
    private final List<String> menuCodes;
    private final boolean enabled;
    public AdminRoleResponse(IamRole role, List<String> menuCodes) {
        this.roleCode = role.getRoleCode(); this.roleName = role.getRoleName();
        this.description = role.getDescription(); this.enabled = role.isEnabled();
        this.menuCodes = Collections.unmodifiableList(new ArrayList<String>(menuCodes));
    }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public String getDescription() { return description; }
    public List<String> getMenuCodes() { return menuCodes; }
    public boolean isEnabled() { return enabled; }
}
