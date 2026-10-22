package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;

import java.util.List;

/** 本次新增：IAM 主数据与关联关系持久化边界。 */
public interface IamManagementRepository {
    List<IamUser> findUsers(boolean includeDisabled);
    IamUser findUser(String userId);
    void insertUser(IamUser user, String operator);
    void updateUser(IamUser user, String operator);

    List<IamRole> findRoles(boolean includeDisabled);
    IamRole findRole(String roleCode);
    void insertRole(IamRole role, String operator);
    void updateRole(IamRole role, String operator);

    List<IamMenu> findMenus(boolean includeDisabled);
    IamMenu findMenu(String menuCode);
    void insertMenu(IamMenu menu, String operator);
    void updateMenu(IamMenu menu, String operator);

    List<String> findRoleCodesByUser(String userId);
    void replaceUserRoles(String userId, List<String> roleCodes, String operator);
    boolean hasEnabledRole(String userId, String roleCode);

    List<String> findMenuCodesByRole(String roleCode);
    void replaceRoleMenus(String roleCode, List<String> menuCodes, String operator);
}
