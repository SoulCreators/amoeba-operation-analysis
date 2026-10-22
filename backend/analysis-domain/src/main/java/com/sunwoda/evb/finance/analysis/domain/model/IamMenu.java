package com.sunwoda.evb.finance.analysis.domain.model;

/** 本次新增：IAM 菜单及按钮权限主数据。 */
public final class IamMenu {
    private final String menuCode;
    private final String menuName;
    private final String parentCode;
    private final String routePath;
    private final String permissionCode;
    private final int displayOrder;
    private final boolean enabled;

    public IamMenu(String menuCode, String menuName, String parentCode, String routePath,
                   String permissionCode, int displayOrder, boolean enabled) {
        this.menuCode = required(menuCode, "菜单编码").toUpperCase(java.util.Locale.ROOT);
        this.menuName = required(menuName, "菜单名称");
        this.parentCode = upperOptional(parentCode);
        this.routePath = optional(routePath);
        this.permissionCode = optional(permissionCode);
        this.displayOrder = displayOrder;
        this.enabled = enabled;
        if (this.menuCode.equals(this.parentCode)) throw new IllegalArgumentException("菜单不能以自身作为父菜单");
    }

    public String getMenuCode() { return menuCode; }
    public String getMenuName() { return menuName; }
    public String getParentCode() { return parentCode; }
    public String getRoutePath() { return routePath; }
    public String getPermissionCode() { return permissionCode; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String upperOptional(String value) {
        String normalized = optional(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }
}
