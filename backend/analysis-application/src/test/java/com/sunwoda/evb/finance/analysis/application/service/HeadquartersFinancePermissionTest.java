package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PermissionAction;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 总部财经最小权限切片的独立矩阵测试。
 *
 * <p>本次新增：固定 HQ_FINANCE、兼容 FINANCE_ADMIN、范围裁剪和 ADMIN 只读边界。</p>
 */
class HeadquartersFinancePermissionTest {
    @Test
    void normalizedHqFinanceGetsAllScopedApplicationActions() {
        PermissionService service = service(new UserScope(
                "U-HQ", " hq_finance ", AnalysisPerspective.BASE,
                "NANCHANG", true, true, false, true));

        // 本次新增：总部财经可在授权范围内查看草稿、历史、明细和导出。
        assertTrue(service.canViewDraft("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canViewHistory("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canViewDetail("U-HQ", AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(service.canExport("U-HQ", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(service.canExport("U-HQ", AnalysisPerspective.BASE, "YICHANG"));
        assertTrue(service.canViewResult("U-HQ", AnalysisPerspective.BASE,
                CalculationResultStatus.CALCULATED));

        // 本次新增：总部财经可执行计算、确认、发布、人工修订和补偿。
        assertTrue(service.canCalculate("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canConfirm("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canPublish("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canManualRevise("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canCompensate("U-HQ", AnalysisPerspective.BASE));
        assertTrue(service.canPerform("U-HQ", AnalysisPerspective.BASE,
                PermissionAction.MANUAL_REVISION));
    }

    @Test
    void legacyFinanceAdminKeepsHeadquartersWriteSemantics() {
        PermissionService service = service(new UserScope(
                "U-LEGACY", " FINANCE_ADMIN ", AnalysisPerspective.BASE,
                "ALL", true, true, false, true));

        // 本次新增：旧 FINANCE_ADMIN 编码继续获得总部财经生命周期权限。
        assertTrue(service.canCalculate("U-LEGACY", AnalysisPerspective.BASE));
        assertTrue(service.canConfirm("U-LEGACY", AnalysisPerspective.BASE));
        assertTrue(service.canPublish("U-LEGACY", AnalysisPerspective.BASE));
        assertTrue(service.canManualRevise("U-LEGACY", AnalysisPerspective.BASE));
        assertTrue(service.canCompensate("U-LEGACY", AnalysisPerspective.BASE));
    }

    @Test
    void headquartersFinanceWriteActionsMustCoverEveryRequestedScope() {
        PermissionService service = service(new UserScope(
                "U-HQ-SCOPE", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, false, true));

        assertTrue(service.canCalculate("U-HQ-SCOPE", AnalysisPerspective.BASE, "NANCHANG"));
        assertFalse(service.canCalculate("U-HQ-SCOPE", AnalysisPerspective.BASE, "YICHANG"));
        assertTrue(service.canCalculate("U-HQ-SCOPE", AnalysisPerspective.BASE,
                new LinkedHashSet<String>(Collections.singletonList("NANCHANG"))));
        assertFalse(service.canCalculate("U-HQ-SCOPE", AnalysisPerspective.BASE,
                new LinkedHashSet<String>(Arrays.asList("NANCHANG", "YICHANG"))));
    }

    @Test
    void adminKeepsReadOnlyAccessAndCannotInheritAnyNewWriteAction() {
        PermissionService service = service(new UserScope(
                "U-ADMIN", "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, false, true));

        // 本次新增：ADMIN 可查看草稿/历史和导出，但不得获得任何总部财经写动作。
        assertTrue(service.canViewDraft("U-ADMIN", AnalysisPerspective.BASE));
        assertTrue(service.canViewHistory("U-ADMIN", AnalysisPerspective.PRODUCT_LINE));
        assertTrue(service.canExport("U-ADMIN", AnalysisPerspective.PRODUCT_LINE, "PL-A"));
        assertFalse(service.canCalculate("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canConfirm("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canPublish("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canManualRevise("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canCompensate("U-ADMIN", AnalysisPerspective.BASE));
        assertFalse(service.canPerform("U-ADMIN", AnalysisPerspective.BASE,
                PermissionAction.PUBLISH));
    }

    private PermissionService service(UserScope... grants) {
        InMemoryScopes repository = new InMemoryScopes();
        for (UserScope grant : grants) repository.save(grant);
        return new PermissionServiceImpl(repository);
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
            if (userId == null || perspective == null) return Collections.emptyList();
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope grant : grants) {
                if (grant.isEnabled()
                        && grant.getUserId().equals(userId)
                        && grant.getPerspective() == perspective) {
                    result.add(grant);
                }
            }
            return result;
        }
    }
}
