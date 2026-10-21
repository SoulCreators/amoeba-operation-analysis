package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceImplTest {
    @Test
    void listsOnlyEnabledScopesForTheRequestedUserAndPerspective() {
        InMemoryScopes repository = new InMemoryScopes();
        repository.save(scope("U001", "BASE_FINANCE", AnalysisPerspective.BASE, "NANCHANG", true));
        repository.save(scope("U001", "BASE_FINANCE", AnalysisPerspective.BASE, "YICHANG", false));
        repository.save(scope("U001", "BASE_FINANCE", AnalysisPerspective.PRODUCT_LINE, "PL-A", true));
        repository.save(scope("U002", "BASE_FINANCE", AnalysisPerspective.BASE, "NANCHANG", true));

        PermissionService service = new PermissionServiceImpl(repository);

        assertEquals(1, service.list(" U001 ", AnalysisPerspective.BASE).size());
        assertEquals("NANCHANG", service.list("U001", AnalysisPerspective.BASE).get(0).getScopeCode());
    }

    @Test
    void grantsExactScopeAndAllScopeButDeniesUnrelatedScope() {
        InMemoryScopes repository = new InMemoryScopes();
        repository.save(scope("U001", "OPS", AnalysisPerspective.BASE, "NANCHANG", true));
        PermissionService service = new PermissionServiceImpl(repository);

        assertTrue(service.canViewScope("U001", AnalysisPerspective.BASE, "nanchang"));
        assertFalse(service.canViewScope("U001", AnalysisPerspective.BASE, "YICHANG"));

        repository.save(scope("U001", "FINANCE", AnalysisPerspective.BASE, "ALL", true));
        assertTrue(service.canViewScope("U001", AnalysisPerspective.BASE, "YICHANG"));
    }

    @Test
    void appliesDetailAndExportCapabilitiesIndependentlyOfScopeVisibility() {
        InMemoryScopes repository = new InMemoryScopes();
        repository.save(new UserScope("U001", "OPS", AnalysisPerspective.BASE,
                "NANCHANG", true, false, false, true));
        repository.save(new UserScope("U001", "OPS", AnalysisPerspective.BASE,
                "YICHANG", false, true, false, true));
        PermissionService service = new PermissionServiceImpl(repository);

        assertTrue(service.canViewScope("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(service.canViewScope("U001", AnalysisPerspective.BASE, "YICHANG"));
        assertTrue(service.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(service.canViewDetail("U001", AnalysisPerspective.BASE, "YICHANG"));
        assertFalse(service.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(service.canExport("U001", AnalysisPerspective.BASE, "YICHANG"));

        Set<String> detailScopes = service.detailScopes("U001", AnalysisPerspective.BASE);
        Set<String> exportScopes = service.exportScopes("U001", AnalysisPerspective.BASE);
        assertEquals(java.util.Collections.singleton("NANCHANG"), detailScopes);
        assertEquals(java.util.Collections.singleton("YICHANG"), exportScopes);
    }

    @Test
    void rejectsBlankUserAndNullPerspectiveBeforeRepositoryAccess() {
        PermissionService service = new PermissionServiceImpl(new InMemoryScopes());

        assertThrows(IllegalArgumentException.class,
                () -> service.list(" ", AnalysisPerspective.BASE));
        assertThrows(IllegalArgumentException.class,
                () -> service.list("U001", null));
    }

    @Test
    void adminGetsReadOnlyAllScopeAccessButCannotPublishAi() {
        InMemoryScopes repository = new InMemoryScopes();
        repository.save(scope("U-ADMIN", "ADMIN", AnalysisPerspective.BASE, "ALL", true));
        repository.save(scope("U-FIN-ADMIN", "FINANCE_ADMIN", AnalysisPerspective.BASE, "ALL", true));
        PermissionService service = new PermissionServiceImpl(repository);

        // 本次修改：ADMIN 可读取草稿，并对所有视角和范围提供只读权限。
        assertTrue(service.canViewDraft("U-ADMIN", AnalysisPerspective.BASE));
        assertTrue(service.canViewDraft("U-ADMIN", AnalysisPerspective.PRODUCT_LINE));
        assertTrue(service.canViewScope("U-ADMIN", AnalysisPerspective.PRODUCT_LINE, "PL-A"));
        assertTrue(service.canViewDetail("U-ADMIN", AnalysisPerspective.PRODUCT_LINE, "PL-A"));
        assertTrue(service.canExport("U-ADMIN", AnalysisPerspective.PRODUCT_LINE, "PL-A"));
        assertEquals(java.util.Collections.singleton("ALL"),
                service.viewableScopes("U-ADMIN", AnalysisPerspective.PRODUCT_LINE));
        assertEquals(java.util.Collections.singleton("ALL"),
                service.detailScopes("U-ADMIN", AnalysisPerspective.PRODUCT_LINE));
        assertEquals(java.util.Collections.singleton("ALL"),
                service.exportScopes("U-ADMIN", AnalysisPerspective.PRODUCT_LINE));

        // 本次修改：只读 ADMIN 不因查看草稿而获得 AI 发布或配置编辑权限。
        assertFalse(service.canPublishAi("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canEditConfig("U-ADMIN", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(service.canViewResult("U-ADMIN", AnalysisPerspective.BASE,
                CalculationResultStatus.CALCULATED));

        assertTrue(service.canViewDraft("U-FIN-ADMIN", AnalysisPerspective.BASE));
        assertTrue(service.canPublishAi("U-FIN-ADMIN", AnalysisPerspective.BASE));
        assertTrue(service.canViewResult("U-FIN-ADMIN", AnalysisPerspective.BASE,
                CalculationResultStatus.CALCULATED));
    }

    private static UserScope scope(String userId, String role, AnalysisPerspective perspective,
                                   String scopeCode, boolean enabled) {
        return new UserScope(userId, role, perspective, scopeCode,
                true, true, false, enabled);
    }

    private static class InMemoryScopes implements UserScopeRepository {
        private final List<UserScope> scopes = new ArrayList<UserScope>();

        @Override
        public UserScope save(UserScope scope) {
            scopes.add(scope);
            return scope;
        }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective) {
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope scope : scopes) {
                if (scope.isEnabled() && scope.getUserId().equals(userId)
                        && scope.getPerspective() == perspective) {
                    result.add(scope);
                }
            }
            return result;
        }
    }
}
