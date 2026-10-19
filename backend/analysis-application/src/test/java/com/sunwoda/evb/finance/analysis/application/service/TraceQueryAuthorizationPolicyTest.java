package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Authorization contract for the planned trace query application service.
 *
 * <p>The trace service must use the same scope and detail capability semantics
 * as the existing permission service: missing identity, disabled grants,
 * missing detail capability, and unrelated scopes are denied by default.</p>
 */
class TraceQueryAuthorizationPolicyTest {
    @Test
    void deniesTraceQueryWithoutIdentity() {
        PermissionService permissions = permissions(
                scope("U001", "FINANCE", "NANCHANG", true, true));

        assertFalse(permissions.canViewDetail(null, AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(permissions.canViewDetail(" ", AnalysisPerspective.BASE, "NANCHANG"));
    }

    @Test
    void deniesTraceQueryWhenGrantHasNoDetailCapability() {
        PermissionService permissions = permissions(
                scope("U001", "FINANCE", "NANCHANG", false, true));

        assertFalse(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
    }

    @Test
    void deniesTraceQueryOutsideAuthorizedScope() {
        PermissionService permissions = permissions(
                scope("U001", "FINANCE", "NANCHANG", true, true));

        assertFalse(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "YICHANG"));
    }

    @Test
    void permitsTraceQueryInAuthorizedScope() {
        PermissionService permissions = permissions(
                scope("U001", "FINANCE", "NANCHANG", true, true));

        assertTrue(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
    }

    @Test
    void permitsTraceQueryForAllScopeGrant() {
        PermissionService permissions = permissions(
                scope("U001", "FINANCE", "ALL", true, true));

        assertTrue(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.canViewDetail("U001", AnalysisPerspective.BASE, "YICHANG"));
    }

    private PermissionService permissions(UserScope... grants) {
        InMemoryScopes repository = new InMemoryScopes();
        for (UserScope grant : grants) {
            repository.save(grant);
        }
        return new PermissionServiceImpl(repository);
    }

    private UserScope scope(String userId, String roleCode, String scopeCode,
                            boolean canViewDetail, boolean enabled) {
        return new UserScope(userId, roleCode, AnalysisPerspective.BASE, scopeCode,
                canViewDetail, false, false, enabled);
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
