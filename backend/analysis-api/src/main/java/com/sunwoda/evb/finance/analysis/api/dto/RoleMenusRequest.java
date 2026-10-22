package com.sunwoda.evb.finance.analysis.api.dto;

import java.util.ArrayList;
import java.util.List;

/** 本次新增：角色菜单整组替换请求。 */
public class RoleMenusRequest {
    private List<String> menuCodes = new ArrayList<String>();
    public List<String> getMenuCodes() { return menuCodes; }
    public void setMenuCodes(List<String> menuCodes) { this.menuCodes = menuCodes; }
}
