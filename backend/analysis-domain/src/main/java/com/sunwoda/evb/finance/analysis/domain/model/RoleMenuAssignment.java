package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：角色菜单授权结果。 */
public final class RoleMenuAssignment {
    private final String roleCode;
    private final List<String> menuCodes;

    public RoleMenuAssignment(String roleCode, List<String> menuCodes) {
        if (roleCode == null || roleCode.trim().isEmpty()) throw new IllegalArgumentException("角色编码不能为空");
        this.roleCode = roleCode.trim().toUpperCase(java.util.Locale.ROOT);
        this.menuCodes = menuCodes == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(menuCodes));
    }

    public String getRoleCode() { return roleCode; }
    public List<String> getMenuCodes() { return menuCodes; }
}
