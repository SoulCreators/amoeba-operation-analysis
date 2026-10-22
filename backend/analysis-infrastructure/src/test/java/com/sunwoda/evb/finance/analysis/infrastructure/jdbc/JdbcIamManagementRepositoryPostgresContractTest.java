package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 本次新增：真实 PostgreSQL 往返验证 IAM 主数据、关联软停用和旧 user_scope 兼容。 */
class JdbcIamManagementRepositoryPostgresContractTest {
    private static JdbcTemplate jdbc;
    private static JdbcIamManagementRepository iam;
    private static JdbcUserScopeRepository scopes;
    private static String userId;
    private static String roleCode;
    private static String menuCode;

    @BeforeAll
    static void connect() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; IAM PostgreSQL contract was not run");
        String username = value(System.getenv("AMOEBA_DB_USERNAME"), "postgres");
        String password = value(System.getenv("AMOEBA_DB_PASSWORD"), "");
        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT user_id FROM iam.platform_user LIMIT 0");
            jdbc.execute("SELECT role_code FROM iam.role LIMIT 0");
            jdbc.execute("SELECT menu_code FROM iam.menu LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V035 IAM schema is not available: " + failure.getMessage());
        }
        iam = new JdbcIamManagementRepository(dataSource);
        scopes = new JdbcUserScopeRepository(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        userId = "IAM-U-" + suffix;
        roleCode = "IAM-R-" + suffix;
        menuCode = "IAM-M-" + suffix;
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null || userId == null) return;
        // 本次新增：测试库只按随机业务键回收夹具；生产 API 仍不提供物理删除。
        jdbc.update("DELETE FROM iam.user_scope WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM iam.role_menu WHERE role_code = ?", roleCode);
        jdbc.update("DELETE FROM iam.user_role WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM iam.menu WHERE menu_code = ?", menuCode);
        jdbc.update("DELETE FROM iam.platform_user WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM iam.role WHERE role_code = ?", roleCode);
    }

    @Test
    void roundTripsMasterDataAndSoftDisablesAssignmentsAndScopes() {
        iam.insertRole(new IamRole(roleCode, "IAM合同角色", null, false, true), "ADMIN-CONTRACT");
        iam.insertMenu(new IamMenu(menuCode, "IAM合同菜单", null, "/contract", "contract:view", 1, true),
                "ADMIN-CONTRACT");
        iam.insertUser(new IamUser(userId, "IAM合同用户", null, "TEST", true), "ADMIN-CONTRACT");
        iam.replaceUserRoles(userId, Collections.singletonList(roleCode), "ADMIN-CONTRACT");
        iam.replaceRoleMenus(roleCode, Collections.singletonList(menuCode), "ADMIN-CONTRACT");
        scopes.replaceForUser(userId, Collections.singletonList(new UserScope(userId, roleCode,
                AnalysisPerspective.BASE, "BASE-CONTRACT", true, true, false, true)));

        assertTrue(iam.hasEnabledRole(userId, roleCode));
        assertEquals(Collections.singletonList(roleCode), iam.findRoleCodesByUser(userId));
        assertEquals(Collections.singletonList(menuCode), iam.findMenuCodesByRole(roleCode));
        assertEquals(1, scopes.findByUser(userId).size());

        iam.replaceUserRoles(userId, Collections.<String>emptyList(), "ADMIN-CONTRACT");
        iam.replaceRoleMenus(roleCode, Collections.<String>emptyList(), "ADMIN-CONTRACT");
        scopes.replaceForUser(userId, Collections.<UserScope>emptyList());

        assertFalse(iam.hasEnabledRole(userId, roleCode));
        assertTrue(iam.findRoleCodesByUser(userId).isEmpty());
        assertTrue(iam.findMenuCodesByRole(roleCode).isEmpty());
        assertTrue(scopes.findByUser(userId).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM iam.user_role WHERE user_id = ? AND enabled = FALSE",
                Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM iam.user_scope WHERE user_id = ? AND enabled = FALSE",
                Integer.class, userId));
    }

    private static String value(String candidate, String fallback) {
        return candidate == null ? fallback : candidate;
    }
}
