package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.service.IamAdminServiceImpl;
import com.sunwoda.evb.finance.analysis.application.service.PermissionServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.IamMenu;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserAuthorization;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本次新增：内存切片验证管理员边界、软停用、原子授权和审计。 */
class IamAdminServiceSliceTest {
    private InMemoryIamManagementRepository iam;
    private InMemoryUserScopeRepository scopes;
    private PermissionServiceImpl permissions;
    private RecordingAudit audit;
    private IamAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        iam = new InMemoryIamManagementRepository();
        scopes = new InMemoryUserScopeRepository();
        permissions = new PermissionServiceImpl(scopes, iam);
        audit = new RecordingAudit();
        service = new IamAdminServiceImpl(iam, scopes, permissions, audit);

        iam.insertRole(new IamRole("ADMIN", "系统管理员", null, true, true), "BOOTSTRAP");
        iam.insertUser(new IamUser("ADMIN-1", "管理员", null, "IT", true), "BOOTSTRAP");
        iam.replaceUserRoles("ADMIN-1", Collections.singletonList("ADMIN"), "BOOTSTRAP");
    }

    @Test
    void managesUserRoleMenuScopeAndWritesAuditWithoutGrantingBusinessWrites() {
        service.createRole("ADMIN-1", new IamRole("BASE_FINANCE", "基地财经", null, false, true));
        service.createMenu("ADMIN-1", new IamMenu("REPORT", "经营报表", null,
                "/reports", "report:view", 10, true));
        service.createUser("ADMIN-1", new IamUser("U-1", "用户一", "u1@example.com", "财经", true));
        service.replaceRoleMenus("ADMIN-1", "BASE_FINANCE", Collections.singletonList("REPORT"));

        UserScope grant = new UserScope("U-1", "BASE_FINANCE", AnalysisPerspective.BASE,
                "BASE-A", true, true, false, true);
        UserAuthorization authorization = service.replaceUserAuthorization("ADMIN-1", "U-1",
                Collections.singletonList("BASE_FINANCE"), Collections.singletonList(grant));

        assertEquals(Collections.singletonList("BASE_FINANCE"), authorization.getRoleCodes());
        assertEquals(1, authorization.getScopes().size());
        assertTrue(permissions.canViewDetail("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertTrue(permissions.canExport("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertFalse(permissions.canEditConfig("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertTrue(permissions.isEnabledAdmin("ADMIN-1"));
        assertFalse(permissions.canCalculate("ADMIN-1", AnalysisPerspective.BASE));
        assertFalse(permissions.canConfirm("ADMIN-1", AnalysisPerspective.BASE));
        assertFalse(permissions.canPublish("ADMIN-1", AnalysisPerspective.BASE));
        assertEquals(5, audit.events.size());

        service.updateRole("ADMIN-1", new IamRole("BASE_FINANCE", "基地财经", null, false, false));
        assertFalse(permissions.canViewDetail("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertFalse(permissions.canExport("U-1", AnalysisPerspective.BASE, "BASE-A"));
        service.updateRole("ADMIN-1", new IamRole("BASE_FINANCE", "基地财经", null, false, true));
        assertTrue(permissions.canViewDetail("U-1", AnalysisPerspective.BASE, "BASE-A"));

        service.updateUser("ADMIN-1", new IamUser("U-1", "用户一", null, "财经", false));
        assertFalse(permissions.canViewDetail("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertFalse(permissions.canExport("U-1", AnalysisPerspective.BASE, "BASE-A"));
        assertTrue(service.listUsers("ADMIN-1", false).stream()
                .noneMatch(user -> "U-1".equals(user.getUserId())));
        assertTrue(service.listUsers("ADMIN-1", true).stream()
                .anyMatch(user -> "U-1".equals(user.getUserId()) && !user.isEnabled()));
    }

    @Test
    void replacementSoftDisablesOldScopeAndRejectsUnassignedScopeRole() {
        service.createRole("ADMIN-1", new IamRole("R1", "角色一", null, false, true));
        service.createUser("ADMIN-1", new IamUser("U-2", "用户二", null, null, true));
        UserScope oldGrant = new UserScope("U-2", "R1", AnalysisPerspective.BASE,
                "OLD", true, false, false, true);
        service.replaceUserAuthorization("ADMIN-1", "U-2", Collections.singletonList("R1"),
                Collections.singletonList(oldGrant));
        service.replaceUserAuthorization("ADMIN-1", "U-2", Collections.singletonList("R1"),
                Collections.<UserScope>emptyList());

        assertFalse(permissions.canViewScope("U-2", AnalysisPerspective.BASE, "OLD"));
        UserScope invalid = new UserScope("U-2", "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, true, true);
        assertThrows(IllegalArgumentException.class, () -> service.replaceUserAuthorization(
                "ADMIN-1", "U-2", Collections.singletonList("R1"), Collections.singletonList(invalid)));
    }

    @Test
    void nonAdminCannotReadOrWriteIam() {
        assertThrows(IllegalStateException.class, () -> service.listUsers("U-NORMAL", true));
        assertThrows(IllegalStateException.class, () -> service.createUser("U-NORMAL",
                new IamUser("U-3", "用户三", null, null, true)));
        assertTrue(audit.events.isEmpty());
    }

    private static final class RecordingAudit implements AuditEventPort {
        private final List<AuditEvent> events = new java.util.ArrayList<AuditEvent>();
        @Override public void append(AuditEvent event) { events.add(event); }
    }
}
