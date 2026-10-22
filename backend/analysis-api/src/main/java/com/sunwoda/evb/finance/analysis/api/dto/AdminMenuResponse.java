package com.sunwoda.evb.finance.analysis.api.dto;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;

/** 本次新增：把数据库 routePath 映射为前端合同中的 path。 */
public final class AdminMenuResponse {
    private final String menuCode;
    private final String menuName;
    private final String parentCode;
    private final String path;
    private final int displayOrder;
    private final boolean enabled;
    public AdminMenuResponse(IamMenu menu) {
        this.menuCode = menu.getMenuCode(); this.menuName = menu.getMenuName();
        this.parentCode = menu.getParentCode(); this.path = menu.getRoutePath();
        this.displayOrder = menu.getDisplayOrder(); this.enabled = menu.isEnabled();
    }
    public String getMenuCode() { return menuCode; }
    public String getMenuName() { return menuName; }
    public String getParentCode() { return parentCode; }
    public String getPath() { return path; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isEnabled() { return enabled; }
}
