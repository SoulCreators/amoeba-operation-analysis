package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;

/** 本次新增：菜单新增/更新请求。 */
public class IamMenuRequest {
    private String menuCode;
    private String menuName;
    private String parentCode;
    private String path;
    private String routePath;
    private String permissionCode;
    private int displayOrder;
    private boolean enabled = true;

    public IamMenu toDomain() {
        return new IamMenu(menuCode, menuName, parentCode, path == null ? routePath : path,
                permissionCode, displayOrder, enabled);
    }
    public String getMenuCode() { return menuCode; }
    public void setMenuCode(String menuCode) { this.menuCode = menuCode; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getParentCode() { return parentCode; }
    public void setParentCode(String parentCode) { this.parentCode = parentCode; }
    public String getRoutePath() { return routePath; }
    public void setRoutePath(String routePath) { this.routePath = routePath; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
