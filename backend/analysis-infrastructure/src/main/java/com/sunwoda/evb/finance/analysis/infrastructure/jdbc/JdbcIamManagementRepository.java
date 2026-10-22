package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.repository.IamManagementRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

/** 本次新增：PostgreSQL IAM 管理中心仓储，关联替换均采用软停用。 */
@Repository
@Primary
@Profile("db")
public class JdbcIamManagementRepository implements IamManagementRepository {
    private final JdbcTemplate jdbc;

    public JdbcIamManagementRepository(DataSource dataSource) { this(new JdbcTemplate(dataSource)); }
    JdbcIamManagementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<IamUser> findUsers(boolean includeDisabled) {
        String sql = "SELECT user_id, employee_no, username, display_name, email, department, enabled FROM iam.platform_user"
                + (includeDisabled ? "" : " WHERE enabled = TRUE") + " ORDER BY user_id";
        return jdbc.query(sql, (rs, row) -> user(rs));
    }
    @Override public IamUser findUser(String userId) {
        List<IamUser> rows = jdbc.query("SELECT user_id, employee_no, username, display_name, email, department, enabled "
                + "FROM iam.platform_user WHERE user_id = ?", (rs, row) -> user(rs), userId);
        return one(rows);
    }
    @Override public void insertUser(IamUser user, String operator) {
        try {
            jdbc.update("INSERT INTO iam.platform_user(user_id, employee_no, username, display_name, email, department, enabled, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", user.getUserId(), user.getEmployeeNo(), user.getUsername(),
                    user.getDisplayName(), user.getEmail(), user.getDepartment(), user.isEnabled(), operator, operator);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("用户已存在: " + user.getUserId()); }
    }
    @Override public void updateUser(IamUser user, String operator) {
        int count = jdbc.update("UPDATE iam.platform_user SET employee_no = ?, username = ?, display_name = ?, email = ?, "
                        + "department = ?, enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                user.getEmployeeNo(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getDepartment(),
                user.isEnabled(), operator, user.getUserId());
        requireUpdated(count, "用户不存在: " + user.getUserId());
    }

    @Override public List<IamRole> findRoles(boolean includeDisabled) {
        String sql = "SELECT role_code, role_name, description, system_role, enabled FROM iam.role"
                + (includeDisabled ? "" : " WHERE enabled = TRUE") + " ORDER BY role_code";
        return jdbc.query(sql, (rs, row) -> new IamRole(rs.getString("role_code"), rs.getString("role_name"),
                rs.getString("description"), rs.getBoolean("system_role"), rs.getBoolean("enabled")));
    }
    @Override public IamRole findRole(String roleCode) {
        return one(jdbc.query("SELECT role_code, role_name, description, system_role, enabled FROM iam.role WHERE role_code = ?",
                (rs, row) -> new IamRole(rs.getString("role_code"), rs.getString("role_name"),
                        rs.getString("description"), rs.getBoolean("system_role"), rs.getBoolean("enabled")), roleCode));
    }
    @Override public void insertRole(IamRole role, String operator) {
        try {
            jdbc.update("INSERT INTO iam.role(role_code, role_name, description, system_role, enabled, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", role.getRoleCode(), role.getRoleName(), role.getDescription(),
                    role.isSystemRole(), role.isEnabled(), operator, operator);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("角色已存在: " + role.getRoleCode()); }
    }
    @Override public void updateRole(IamRole role, String operator) {
        int count = jdbc.update("UPDATE iam.role SET role_name = ?, description = ?, system_role = ?, enabled = ?, "
                        + "updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE role_code = ?", role.getRoleName(),
                role.getDescription(), role.isSystemRole(), role.isEnabled(), operator, role.getRoleCode());
        requireUpdated(count, "角色不存在: " + role.getRoleCode());
    }

    @Override public List<IamMenu> findMenus(boolean includeDisabled) {
        String sql = "SELECT menu_code, menu_name, parent_code, route_path, permission_code, display_order, enabled FROM iam.menu"
                + (includeDisabled ? "" : " WHERE enabled = TRUE") + " ORDER BY display_order, menu_code";
        return jdbc.query(sql, (rs, row) -> menu(rs));
    }
    @Override public IamMenu findMenu(String menuCode) {
        return one(jdbc.query("SELECT menu_code, menu_name, parent_code, route_path, permission_code, display_order, enabled "
                + "FROM iam.menu WHERE menu_code = ?", (rs, row) -> menu(rs), menuCode));
    }
    @Override public void insertMenu(IamMenu menu, String operator) {
        try {
            jdbc.update("INSERT INTO iam.menu(menu_code, menu_name, parent_code, route_path, permission_code, display_order, enabled, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", menu.getMenuCode(), menu.getMenuName(), menu.getParentCode(),
                    menu.getRoutePath(), menu.getPermissionCode(), menu.getDisplayOrder(), menu.isEnabled(), operator, operator);
        } catch (DuplicateKeyException ex) { throw new IllegalArgumentException("菜单已存在: " + menu.getMenuCode()); }
    }
    @Override public void updateMenu(IamMenu menu, String operator) {
        int count = jdbc.update("UPDATE iam.menu SET menu_name = ?, parent_code = ?, route_path = ?, permission_code = ?, "
                        + "display_order = ?, enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE menu_code = ?",
                menu.getMenuName(), menu.getParentCode(), menu.getRoutePath(), menu.getPermissionCode(), menu.getDisplayOrder(),
                menu.isEnabled(), operator, menu.getMenuCode());
        requireUpdated(count, "菜单不存在: " + menu.getMenuCode());
    }

    @Override public List<String> findRoleCodesByUser(String userId) {
        return jdbc.query("SELECT role_code FROM iam.user_role WHERE user_id = ? AND enabled = TRUE ORDER BY role_code",
                (rs, row) -> rs.getString("role_code"), userId);
    }
    @Override public void replaceUserRoles(String userId, List<String> roleCodes, String operator) {
        jdbc.update("UPDATE iam.user_role SET enabled = FALSE, updated_by = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND enabled = TRUE", operator, userId);
        for (String roleCode : safe(roleCodes)) {
            jdbc.update("INSERT INTO iam.user_role(user_id, role_code, enabled, created_by, updated_by) VALUES (?, ?, TRUE, ?, ?) "
                            + "ON CONFLICT (user_id, role_code) DO UPDATE SET enabled = TRUE, updated_by = EXCLUDED.updated_by, "
                            + "updated_at = CURRENT_TIMESTAMP", userId, roleCode, operator, operator);
        }
    }
    @Override public boolean hasEnabledRole(String userId, String roleCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM iam.user_role ur "
                        + "JOIN iam.platform_user u ON u.user_id = ur.user_id AND u.enabled = TRUE "
                        + "JOIN iam.role r ON r.role_code = ur.role_code AND r.enabled = TRUE "
                        + "WHERE ur.user_id = ? AND ur.role_code = ? AND ur.enabled = TRUE", Integer.class, userId, roleCode);
        return count != null && count > 0;
    }
    @Override public List<String> findMenuCodesByRole(String roleCode) {
        return jdbc.query("SELECT menu_code FROM iam.role_menu WHERE role_code = ? AND enabled = TRUE ORDER BY menu_code",
                (rs, row) -> rs.getString("menu_code"), roleCode);
    }
    @Override public void replaceRoleMenus(String roleCode, List<String> menuCodes, String operator) {
        jdbc.update("UPDATE iam.role_menu SET enabled = FALSE, updated_by = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE role_code = ? AND enabled = TRUE", operator, roleCode);
        for (String menuCode : safe(menuCodes)) {
            jdbc.update("INSERT INTO iam.role_menu(role_code, menu_code, enabled, created_by, updated_by) VALUES (?, ?, TRUE, ?, ?) "
                            + "ON CONFLICT (role_code, menu_code) DO UPDATE SET enabled = TRUE, updated_by = EXCLUDED.updated_by, "
                            + "updated_at = CURRENT_TIMESTAMP", roleCode, menuCode, operator, operator);
        }
    }

    private static IamMenu menu(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IamMenu(rs.getString("menu_code"), rs.getString("menu_name"), rs.getString("parent_code"),
                rs.getString("route_path"), rs.getString("permission_code"), rs.getInt("display_order"), rs.getBoolean("enabled"));
    }
    private static IamUser user(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IamUser(rs.getString("user_id"), rs.getString("employee_no"), rs.getString("username"),
                rs.getString("display_name"), rs.getString("email"), rs.getString("department"),
                rs.getBoolean("enabled"));
    }
    private static <T> T one(List<T> rows) { return rows == null || rows.isEmpty() ? null : rows.get(0); }
    private static List<String> safe(List<String> values) { return values == null ? Collections.<String>emptyList() : values; }
    private static void requireUpdated(int count, String message) { if (count == 0) throw new IllegalArgumentException(message); }
}
