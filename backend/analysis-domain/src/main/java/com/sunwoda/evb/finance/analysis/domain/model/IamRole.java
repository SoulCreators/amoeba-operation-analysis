package com.sunwoda.evb.finance.analysis.domain.model;

/** 本次新增：IAM 角色主数据。systemRole 仅标识内置角色，不隐含经营写权限。 */
public final class IamRole {
    private final String roleCode;
    private final String roleName;
    private final String description;
    private final boolean systemRole;
    private final boolean enabled;

    public IamRole(String roleCode, String roleName, String description,
                   boolean systemRole, boolean enabled) {
        this.roleCode = required(roleCode, "角色编码").toUpperCase(java.util.Locale.ROOT);
        this.roleName = required(roleName, "角色名称");
        this.description = optional(description);
        this.systemRole = systemRole;
        this.enabled = enabled;
    }

    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public String getDescription() { return description; }
    public boolean isSystemRole() { return systemRole; }
    public boolean isEnabled() { return enabled; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
