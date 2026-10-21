package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactTraceQuery;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADMIN 全量只读权限的应用层回归契约。
 *
 * <p>本测试只使用现有 UserScope 和应用服务策略，不修改生产 SQL；它将
 * “全量读取”与“写动作”拆开，避免管理员角色被错误地当成业务发布角色。</p>
 */
@Tag("ADMIN-READONLY")
class AdminReadOnlyPermissionRegressionTest {
    private static final String ADMIN_USER = "IT-ADMIN-001";

    @Test
    void adminAllScopeGrantCanReadPublishedSummaryDetailExportAndTrace() {
        PermissionService permissions = permissions(adminGrant());

        // 本次新增：锁定 ADMIN 通过 ALL 范围读取不同业务范围的汇总和明细。
        assertTrue(permissions.canViewScope(ADMIN_USER, AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.canViewScope(ADMIN_USER, AnalysisPerspective.BASE, "YICHANG"));
        assertTrue(permissions.canViewDetail(ADMIN_USER, AnalysisPerspective.BASE, "NANCHANG"));
        assertTrue(permissions.canExport(ADMIN_USER, AnalysisPerspective.BASE, "YICHANG"));
        assertEquals(Collections.singleton("ALL"),
                permissions.viewableScopes(ADMIN_USER, AnalysisPerspective.BASE));
        assertEquals(Collections.singleton("ALL"),
                permissions.detailScopes(ADMIN_USER, AnalysisPerspective.BASE));
        assertEquals(Collections.singleton("ALL"),
                permissions.exportScopes(ADMIN_USER, AnalysisPerspective.BASE));
        assertTrue(permissions.canViewResult(ADMIN_USER, AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED));

        // 本次新增：锁定导出策略复用同一 ALL 范围，并允许明细导出读取。
        ExportPermission export = new ExportPermissionPolicy(permissions).authorize(
                ADMIN_USER, AnalysisPerspective.BASE, CalculationResultStatus.PUBLISHED,
                setOf("NANCHANG", "YICHANG"));
        assertTrue(export.isAllowed());
        assertEquals(setOf("NANCHANG", "YICHANG"), export.getSummaryScopes());
        assertEquals(setOf("NANCHANG", "YICHANG"), export.getDetailScopes());

        // 本次新增：锁定追溯应用服务使用 ADMIN 的 ALL 明细范围读取全部来源行。
        PnlFactTraceQuery sourceQuery = (batchNo, perspective, taskNo, userId) -> Arrays.asList(
                source("NANCHANG"), source("YICHANG"));
        List<PnlFactSource> sources = new TraceQueryServiceImpl(permissions, sourceQuery)
                .findForUser("BATCH-ADMIN", AnalysisPerspective.BASE, "TASK-ADMIN", ADMIN_USER);
        assertEquals(2, sources.size());
    }

    @Test
    void adminAllScopeGrantDoesNotImplyConfigurationOrAiPublishActions() {
        PermissionService permissions = permissions(adminGrant());

        // 本次新增：锁定 ADMIN 全量只读授权不能转化为配置写权限。
        assertFalse(permissions.canEditConfig(ADMIN_USER, AnalysisPerspective.BASE, "NANCHANG"));
        // 本次新增：锁定 AI 草稿发布仍是独立写动作，不能由只读权限推导。
        assertFalse(permissions.canPublishAi(ADMIN_USER, AnalysisPerspective.BASE));
    }

    @Test
    void adminRoleCanReadUnpublishedResultWithoutBeingFinanceRole() {
        PermissionService permissions = permissions(adminGrant());

        // 本次新增：ADMIN 全量只读应覆盖 CALCULATED/CONFIRMED 草稿读取。
        assertTrue(permissions.canViewDraft(ADMIN_USER, AnalysisPerspective.BASE));
        assertTrue(permissions.canViewResult(ADMIN_USER, AnalysisPerspective.BASE,
                CalculationResultStatus.CALCULATED));
        assertTrue(permissions.canViewResult(ADMIN_USER, AnalysisPerspective.BASE,
                CalculationResultStatus.CONFIRMED));
    }

    private PermissionService permissions(UserScope... grants) {
        InMemoryScopes repository = new InMemoryScopes();
        for (UserScope grant : grants) repository.save(grant);
        return new PermissionServiceImpl(repository);
    }

    private UserScope adminGrant() {
        return new UserScope(ADMIN_USER, "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, false, true);
    }

    private PnlFactSource source(String scopeCode) {
        return new PnlFactSource("TASK-ADMIN", "admin.xlsx", "checksum",
                "Sheet1", "NANCHANG".equals(scopeCode) ? 2 : 3,
                scopeCode, "BASE-001", "PRODUCT-001", "LINE-001");
    }

    private java.util.Set<String> setOf(String... values) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<String>();
        Collections.addAll(result, values);
        return result;
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
