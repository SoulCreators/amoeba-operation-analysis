package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.repository.IamManagementRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 本次新增：本地联调和应用单测使用的 IAM 内存仓储。 */
@Repository
public class InMemoryIamManagementRepository implements IamManagementRepository {
    private final Map<String, IamUser> users = new LinkedHashMap<String, IamUser>();
    private final Map<String, IamRole> roles = new LinkedHashMap<String, IamRole>();
    private final Map<String, IamMenu> menus = new LinkedHashMap<String, IamMenu>();
    private final Map<String, Set<String>> userRoles = new LinkedHashMap<String, Set<String>>();
    private final Map<String, Set<String>> roleMenus = new LinkedHashMap<String, Set<String>>();

    @Override public synchronized List<IamUser> findUsers(boolean includeDisabled) {
        List<IamUser> result = new ArrayList<IamUser>();
        for (IamUser value : users.values()) if (includeDisabled || value.isEnabled()) result.add(value);
        return Collections.unmodifiableList(result);
    }
    @Override public synchronized IamUser findUser(String userId) { return users.get(userId); }
    @Override public synchronized void insertUser(IamUser user, String operator) {
        if (users.containsKey(user.getUserId())) throw new IllegalArgumentException("用户已存在: " + user.getUserId());
        users.put(user.getUserId(), user);
    }
    @Override public synchronized void updateUser(IamUser user, String operator) {
        require(users.containsKey(user.getUserId()), "用户不存在: " + user.getUserId());
        users.put(user.getUserId(), user);
    }

    @Override public synchronized List<IamRole> findRoles(boolean includeDisabled) {
        List<IamRole> result = new ArrayList<IamRole>();
        for (IamRole value : roles.values()) if (includeDisabled || value.isEnabled()) result.add(value);
        return Collections.unmodifiableList(result);
    }
    @Override public synchronized IamRole findRole(String roleCode) { return roles.get(roleCode); }
    @Override public synchronized void insertRole(IamRole role, String operator) {
        if (roles.containsKey(role.getRoleCode())) throw new IllegalArgumentException("角色已存在: " + role.getRoleCode());
        roles.put(role.getRoleCode(), role);
    }
    @Override public synchronized void updateRole(IamRole role, String operator) {
        require(roles.containsKey(role.getRoleCode()), "角色不存在: " + role.getRoleCode());
        roles.put(role.getRoleCode(), role);
    }

    @Override public synchronized List<IamMenu> findMenus(boolean includeDisabled) {
        List<IamMenu> result = new ArrayList<IamMenu>();
        for (IamMenu value : menus.values()) if (includeDisabled || value.isEnabled()) result.add(value);
        return Collections.unmodifiableList(result);
    }
    @Override public synchronized IamMenu findMenu(String menuCode) { return menus.get(menuCode); }
    @Override public synchronized void insertMenu(IamMenu menu, String operator) {
        if (menus.containsKey(menu.getMenuCode())) throw new IllegalArgumentException("菜单已存在: " + menu.getMenuCode());
        menus.put(menu.getMenuCode(), menu);
    }
    @Override public synchronized void updateMenu(IamMenu menu, String operator) {
        require(menus.containsKey(menu.getMenuCode()), "菜单不存在: " + menu.getMenuCode());
        menus.put(menu.getMenuCode(), menu);
    }

    @Override public synchronized List<String> findRoleCodesByUser(String userId) {
        return copy(userRoles.get(userId));
    }
    @Override public synchronized void replaceUserRoles(String userId, List<String> roleCodes, String operator) {
        userRoles.put(userId, new LinkedHashSet<String>(roleCodes));
    }
    @Override public synchronized boolean hasEnabledRole(String userId, String roleCode) {
        IamUser user = users.get(userId);
        IamRole role = roles.get(roleCode);
        Set<String> assigned = userRoles.get(userId);
        return user != null && user.isEnabled() && role != null && role.isEnabled()
                && assigned != null && assigned.contains(roleCode);
    }
    @Override public synchronized List<String> findMenuCodesByRole(String roleCode) {
        return copy(roleMenus.get(roleCode));
    }
    @Override public synchronized void replaceRoleMenus(String roleCode, List<String> menuCodes, String operator) {
        roleMenus.put(roleCode, new LinkedHashSet<String>(menuCodes));
    }

    private static List<String> copy(Set<String> source) {
        return source == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(source));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
