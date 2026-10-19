package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Export authorization contract for the permissions currently exposed by the application layer.
 *
 * <p>There is not yet an export/download endpoint in the repository. These tests therefore
 * assert the actual permission service contract that an eventual exporter must consume:
 * identity, perspective, scope, export capability, detail capability, and published status.</p>
 */
class ExportAuthorizationPolicyTest {
    @Test
    void deniesExportWithoutIdentity() {
        PermissionService permissions = permissions(
                grant("U001", "FINANCE", "NANCHANG", true, true));

        assertFalse(permissions.canExport(null, AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canExport(" ", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canViewDetail(null, AnalysisPerspective.BASE, "NANCHANG"));
    }

    @Test
    void deniesExportWhenScopeGrantHasNoExportCapability() {
        PermissionService permissions = permissions(
                grant("U001", "FINANCE", "NANCHANG", true, false));

        assertTrue(permissions.canViewScope("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.exportScopes("U001", AnalysisPerspective.BASE).isEmpty());
    }

    @Test
    void permitsSummaryExportButNotDetailWhenExportHasNoDetailCapability() {
        PermissionService permissions = permissions(
                grant("U001", "OPS", "NANCHANG", false, true));

        assertTrue(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertEquals(Collections.singleton("NANCHANG"),
                permissions.exportScopes("U001", AnalysisPerspective.BASE));
        assertTrue(permissions.detailScopes("U001", AnalysisPerspective.BASE).isEmpty());
    }

    @Test
    void permitsDetailExportWhenBothExportAndDetailAreGranted() {
        PermissionService permissions = permissions(
                grant("U001", "FINANCE", "NANCHANG", true, true));

        assertTrue(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertEquals(Collections.singleton("NANCHANG"),
                permissions.detailScopes("U001", AnalysisPerspective.BASE));
    }

    @Test
    void deniesExportOutsideTheGrantedScope() {
        PermissionService permissions = permissions(
                grant("U001", "FINANCE", "NANCHANG", true, true));

        assertTrue(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canExport("U001", AnalysisPerspective.BASE, "YICHANG"));
        assertFalse(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "YICHANG"));
    }

    @Test
    void deniesUnpublishedResultToNonFinanceEvenWhenExportIsGranted() {
        PermissionService permissions = permissions(
                grant("U001", "OPS", "NANCHANG", false, true));

        assertTrue(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canViewResult("U001", AnalysisPerspective.BASE,
                CalculationResultStatus.CALCULATED));
        assertFalse(permissions.canViewResult("U001", AnalysisPerspective.BASE,
                CalculationResultStatus.CONFIRMED));
        assertTrue(permissions.canViewResult("U001", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED));
    }

    @Test
    void bindsDownloadAuthorizationToTheRequestingIdentity() {
        PermissionService permissions = permissions(
                grant("U001", "FINANCE", "NANCHANG", false, true),
                grant("U002", "FINANCE", "YICHANG", false, true));

        assertTrue(permissions.canExport("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canExport("U002", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canExport("U001", AnalysisPerspective.BASE, "YICHANG"));
        assertEquals(Collections.singleton("NANCHANG"),
                permissions.exportScopes("U001", AnalysisPerspective.BASE));
        assertEquals(Collections.singleton("YICHANG"),
                permissions.exportScopes("U002", AnalysisPerspective.BASE));
    }

    @Test
    void policyKeepsSummaryExportWhenDetailIsNotGranted() {
        PermissionService permissions = permissions(
                grant("U001", "OPS", "NANCHANG", false, true));

        ExportPermission decision = new ExportPermissionPolicy(permissions)
                .authorize("U001", AnalysisPerspective.BASE,
                        CalculationResultStatus.PUBLISHED);

        assertTrue(decision.isAllowed());
        assertTrue(decision.canExportSummary("NANCHANG"));
        assertFalse(decision.canExportDetail("NANCHANG"));
    }

    @Test
    void policyRejectsUnpublishedExportForNonFinanceRole() {
        PermissionService permissions = permissions(
                grant("U001", "OPS", "NANCHANG", false, true));

        ExportPermission decision = new ExportPermissionPolicy(permissions)
                .authorize("U001", AnalysisPerspective.BASE,
                        CalculationResultStatus.CONFIRMED);

        assertFalse(decision.isAllowed());
        assertFalse(decision.canExportSummary("NANCHANG"));
    }

    private PermissionService permissions(UserScope... grants) {
        InMemoryScopes repository = new InMemoryScopes();
        for (UserScope grant : grants) {
            repository.save(grant);
        }
        return new PermissionServiceImpl(repository);
    }

    private UserScope grant(String userId, String roleCode, String scopeCode,
                            boolean canViewDetail, boolean canExport) {
        return new UserScope(userId, roleCode, AnalysisPerspective.BASE, scopeCode,
                canViewDetail, canExport, false, true);
    }

    private static final class InMemoryScopes implements UserScopeRepository {
        private final List<UserScope> grants = new ArrayList<UserScope>();

        @Override
        public UserScope save(UserScope scope) {
            grants.add(scope);
            return scope;
        }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId,
                                                         AnalysisPerspective perspective) {
            if (userId == null || perspective == null) {
                return Collections.emptyList();
            }
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope grant : grants) {
                if (grant.isEnabled() && grant.getUserId().equals(userId)
                        && grant.getPerspective() == perspective) {
                    result.add(grant);
                }
            }
            return result;
        }
    }
}
