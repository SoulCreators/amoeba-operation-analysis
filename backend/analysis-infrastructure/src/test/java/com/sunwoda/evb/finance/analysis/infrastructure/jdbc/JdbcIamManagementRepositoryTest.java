package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 本次新增：锁定 JDBC 关联替换的软停用和恢复语义。 */
class JdbcIamManagementRepositoryTest {
    @Test
    void userRolesAreSoftDisabledThenUpserted() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcIamManagementRepository repository = new JdbcIamManagementRepository(jdbc);

        repository.replaceUserRoles("U-1", Arrays.asList("R1", "R2"), "ADMIN-1");

        verify(jdbc).update(contains("UPDATE iam.user_role SET enabled = FALSE"), eq("ADMIN-1"), eq("U-1"));
        verify(jdbc).update(contains("ON CONFLICT (user_id, role_code) DO UPDATE"),
                eq("U-1"), eq("R1"), eq("ADMIN-1"), eq("ADMIN-1"));
        verify(jdbc).update(contains("ON CONFLICT (user_id, role_code) DO UPDATE"),
                eq("U-1"), eq("R2"), eq("ADMIN-1"), eq("ADMIN-1"));
    }

    @Test
    void emptyRoleMenusOnlySoftDisablesExistingAssignments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcIamManagementRepository repository = new JdbcIamManagementRepository(jdbc);

        repository.replaceRoleMenus("R1", Collections.<String>emptyList(), "ADMIN-1");

        verify(jdbc).update(contains("UPDATE iam.role_menu SET enabled = FALSE"), eq("ADMIN-1"), eq("R1"));
    }
}
