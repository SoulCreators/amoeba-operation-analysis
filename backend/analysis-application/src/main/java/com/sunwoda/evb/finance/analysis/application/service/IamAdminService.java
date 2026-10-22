package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.RoleMenuAssignment;
import com.sunwoda.evb.finance.analysis.domain.model.UserAuthorization;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.List;

/** 本次新增：ADMIN 专用用户、角色、菜单和数据权限管理用例。 */
public interface IamAdminService {
    List<IamUser> listUsers(String operator, boolean includeDisabled);
    IamUser getUser(String operator, String userId);
    IamUser createUser(String operator, IamUser user);
    IamUser createUser(String operator, IamUser user, List<String> roleCodes,
                       List<DataScopeSelection> scopes);
    IamUser updateUser(String operator, IamUser user);
    IamUser updateUser(String operator, IamUser user, List<String> roleCodes,
                       List<DataScopeSelection> scopes);
    List<IamRole> listRoles(String operator, boolean includeDisabled);
    IamRole getRole(String operator, String roleCode);
    IamRole createRole(String operator, IamRole role);
    IamRole createRole(String operator, IamRole role, List<String> menuCodes);
    IamRole updateRole(String operator, IamRole role);
    IamRole updateRole(String operator, IamRole role, List<String> menuCodes);
    List<IamMenu> listMenus(String operator, boolean includeDisabled);
    IamMenu createMenu(String operator, IamMenu menu);
    IamMenu updateMenu(String operator, IamMenu menu);
    UserAuthorization getUserAuthorization(String operator, String userId);
    UserAuthorization replaceUserAuthorization(String operator, String userId,
                                               List<String> roleCodes, List<UserScope> scopes);
    UserAuthorization replaceUserScopes(String operator, String userId,
                                        List<DataScopeSelection> scopes);
    RoleMenuAssignment getRoleMenus(String operator, String roleCode);
    RoleMenuAssignment replaceRoleMenus(String operator, String roleCode, List<String> menuCodes);
}
