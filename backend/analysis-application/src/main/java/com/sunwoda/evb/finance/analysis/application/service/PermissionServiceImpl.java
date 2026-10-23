package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PermissionAction;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PermissionServiceImpl implements PermissionService {
    private static final String ADMIN_ROLE = "ADMIN";
    // 本次新增：HQ_FINANCE 是总部财经的明确语义，FINANCE_ADMIN 作为历史角色编码继续兼容。
    private static final String HQ_FINANCE_ROLE = "HQ_FINANCE";
    private static final String LEGACY_HQ_FINANCE_ROLE = "FINANCE_ADMIN";

    private final UserScopeRepository repository;

    public PermissionServiceImpl(UserScopeRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserScope save(UserScope scope) {
        return repository.save(scope);
    }

    @Override
    public List<UserScope> list(String userId, AnalysisPerspective perspective) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        if (perspective == null) throw new IllegalArgumentException("数据视角不能为空");
        List<UserScope> scopes = repository.findByUserAndPerspective(userId.trim(), perspective);
        return scopes == null ? Collections.<UserScope>emptyList() : scopes;
    }

    @Override
    public boolean canViewScope(String userId, AnalysisPerspective perspective, String scopeCode) {
        return hasGrant(userId, perspective, scopeCode, PermissionAction.VIEW_SCOPE);
    }

    @Override
    public boolean canViewDetail(String userId, AnalysisPerspective perspective, String scopeCode) {
        return hasGrant(userId, perspective, scopeCode, PermissionAction.VIEW_DETAIL);
    }

    @Override
    public boolean canExport(String userId, AnalysisPerspective perspective, String scopeCode) {
        return hasGrant(userId, perspective, scopeCode, PermissionAction.EXPORT);
    }

    @Override
    public boolean canEditConfig(String userId, AnalysisPerspective perspective, String scopeCode) {
        return hasGrant(userId, perspective, scopeCode, PermissionAction.EDIT_CONFIG);
    }

    @Override
    public boolean canViewDraft(String userId, AnalysisPerspective perspective) {
        if (perspective == null) return false;
        // 本次修改：ADMIN 仅获得全量读取草稿能力，保持与写操作授权完全隔离。
        return hasAdminRole(userId) || hasFinanceRole(userId, perspective);
    }

    @Override
    public boolean isEnabledAdmin(String userId) {
        // 本次新增：HTTP 写门禁复用同一已启用 ADMIN 判定，不信任客户端角色字段。
        return hasAdminRole(userId);
    }

    @Override
    public boolean canViewHistory(String userId, AnalysisPerspective perspective) {
        // 本次新增：总部财经与 ADMIN 均可读取历史；ADMIN 仍只获得读取能力。
        return perspective != null && (hasAdminRole(userId) || hasFinanceRole(userId, perspective));
    }

    @Override
    public boolean canViewResult(String userId, AnalysisPerspective perspective,
                                 CalculationResultStatus status) {
        if (status == null) return false;
        if (status != CalculationResultStatus.PUBLISHED && !canViewDraft(userId, perspective)) {
            return false;
        }
        return !viewableScopes(userId, perspective).isEmpty();
    }

    @Override
    public boolean canPublishAi(String userId, AnalysisPerspective perspective) {
        // 本次修改：ADMIN 的草稿读取权不升级为 AI 发布写权限，仍仅沿用财经角色规则。
        return hasFinanceRole(userId, perspective);
    }

    @Override
    public boolean canCalculate(String userId, AnalysisPerspective perspective) {
        return canPerform(userId, perspective, PermissionAction.CALCULATE);
    }

    @Override
    public boolean canConfirm(String userId, AnalysisPerspective perspective) {
        return canPerform(userId, perspective, PermissionAction.CONFIRM);
    }

    @Override
    public boolean canPublish(String userId, AnalysisPerspective perspective) {
        return canPerform(userId, perspective, PermissionAction.PUBLISH);
    }

    @Override
    public boolean canManualRevise(String userId, AnalysisPerspective perspective) {
        return canPerform(userId, perspective, PermissionAction.MANUAL_REVISION);
    }

    @Override
    public boolean canCompensate(String userId, AnalysisPerspective perspective) {
        return canPerform(userId, perspective, PermissionAction.COMPENSATE);
    }

    @Override
    public boolean canPerform(String userId, AnalysisPerspective perspective, PermissionAction action) {
        if (perspective == null || action == null) return false;
        switch (action) {
            case VIEW_DRAFT:
                return canViewDraft(userId, perspective);
            case VIEW_HISTORY:
                return canViewHistory(userId, perspective);
            case CALCULATE:
            case CONFIRM:
            case PUBLISH:
            case MANUAL_REVISION:
            case COMPENSATE:
            case ADJUSTMENT:
                // 本次新增：所有新增写动作只授予当前视角下有授权范围的总部财经角色。
                // 本次新增：ADMIN 即使拥有全量读取范围，也不会命中总部财经写动作分支。
                return hasHeadquartersFinanceRole(userId, perspective);
            default:
                // 本次新增：范围型动作继续由既有 canView*/canExport/canEditConfig 方法判定。
                return false;
        }
    }

    @Override
    public boolean canPerform(String userId, AnalysisPerspective perspective,
                              PermissionAction action, String scopeCode) {
        if (perspective == null || action == null
                || scopeCode == null || scopeCode.trim().isEmpty()) return false;
        switch (action) {
            case VIEW_SCOPE:
                return canViewScope(userId, perspective, scopeCode);
            case VIEW_DETAIL:
                return canViewDetail(userId, perspective, scopeCode);
            case EXPORT:
                return canExport(userId, perspective, scopeCode);
            case EDIT_CONFIG:
                return canEditConfig(userId, perspective, scopeCode);
            case VIEW_DRAFT:
            case VIEW_HISTORY:
                // 本次新增：草稿与历史的 scope 入口只负责读取，具体明细仍由读取范围裁剪。
                return canPerform(userId, perspective, action);
            case CALCULATE:
            case CONFIRM:
            case PUBLISH:
            case MANUAL_REVISION:
            case COMPENSATE:
            case ADJUSTMENT:
                // 本次新增：HQ_FINANCE 的最大业务权限仍受当前动作涉及的实际 scope 限制。
                return hasHeadquartersFinanceGrant(userId, perspective, scopeCode);
            default:
                return false;
        }
    }

    @Override
    public Set<String> viewableScopes(String userId, AnalysisPerspective perspective) {
        return scopesFor(userId, perspective, PermissionAction.VIEW_SCOPE);
    }

    @Override
    public Set<String> detailScopes(String userId, AnalysisPerspective perspective) {
        return scopesFor(userId, perspective, PermissionAction.VIEW_DETAIL);
    }

    @Override
    public Set<String> exportScopes(String userId, AnalysisPerspective perspective) {
        return scopesFor(userId, perspective, PermissionAction.EXPORT);
    }

    private boolean hasGrant(String userId, AnalysisPerspective perspective, String scopeCode,
                             PermissionAction action) {
        if (scopeCode == null || scopeCode.trim().isEmpty()) return false;
        if (perspective == null) return false;
        // 本次修改：ADMIN 只在查看范围、明细和导出三个读取动作上绕过范围明细授权；配置编辑仍走原始显式授权。
        if (hasAdminRole(userId) && isAdminReadAction(action)) return true;
        for (UserScope scope : safeList(userId, perspective)) {
            if (scope.matchesScope(scopeCode) && scope.allows(action)) return true;
        }
        return false;
    }

    private Set<String> scopesFor(String userId, AnalysisPerspective perspective,
                                  PermissionAction action) {
        Set<String> result = new LinkedHashSet<String>();
        if (perspective == null) return result;
        // 本次修改：用 ALL 表示 ADMIN 在当前读取视角下拥有全部范围，供结果查询和导出策略复用。
        if (hasAdminRole(userId) && isAdminReadAction(action)) {
            result.add("ALL");
            return result;
        }
        for (UserScope scope : safeList(userId, perspective)) {
            if (scope.allows(action)) result.add(normalizeScope(scope.getScopeCode()));
        }
        return result;
    }

    private boolean hasAdminRole(String userId) {
        if (userId == null || userId.trim().isEmpty()) return false;
        // 本次修改：管理员角色是全局只读角色，跨已登记视角识别，避免只因当前视角没有授权行而漏掉全量读取权。
        for (AnalysisPerspective candidate : AnalysisPerspective.values()) {
            for (UserScope scope : safeList(userId, candidate)) {
                if (isEnabledRole(scope, ADMIN_ROLE)) return true;
            }
        }
        return false;
    }

    private boolean hasFinanceRole(String userId, AnalysisPerspective perspective) {
        for (UserScope scope : safeList(userId, perspective)) {
            if (!scope.isEnabled()) continue;
            String role = normalizeRole(scope.getRoleCode());
            if (role.contains("FINANCE")) return true;
        }
        return false;
    }

    private boolean hasHeadquartersFinanceRole(String userId, AnalysisPerspective perspective) {
        for (UserScope scope : safeList(userId, perspective)) {
            if (!scope.isEnabled() || !scope.allows(PermissionAction.VIEW_SCOPE)) continue;
            String role = normalizeRole(scope.getRoleCode());
            // 本次新增：只把明确 HQ_FINANCE 和兼容的 FINANCE_ADMIN 识别为总部财经写角色。
            if (HQ_FINANCE_ROLE.equals(role) || LEGACY_HQ_FINANCE_ROLE.equals(role)) return true;
        }
        return false;
    }

    private boolean hasHeadquartersFinanceGrant(String userId, AnalysisPerspective perspective,
                                                String scopeCode) {
        String normalizedScope = normalizeScope(scopeCode);
        for (UserScope scope : safeList(userId, perspective)) {
            if (!scope.isEnabled() || !scope.allows(PermissionAction.VIEW_SCOPE)
                    || !scope.matchesScope(normalizedScope)) continue;
            String role = normalizeRole(scope.getRoleCode());
            // 本次新增：只有总部财经角色且授权行覆盖该 scope 才能执行生命周期写动作。
            if (HQ_FINANCE_ROLE.equals(role) || LEGACY_HQ_FINANCE_ROLE.equals(role)) return true;
        }
        return false;
    }

    private String normalizeRole(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isEnabledRole(UserScope scope, String roleCode) {
        return scope != null && scope.isEnabled() && roleCode.equalsIgnoreCase(scope.getRoleCode().trim());
    }

    private boolean isAdminReadAction(PermissionAction action) {
        return action == PermissionAction.VIEW_SCOPE
                || action == PermissionAction.VIEW_DETAIL
                || action == PermissionAction.EXPORT;
    }

    private List<UserScope> safeList(String userId, AnalysisPerspective perspective) {
        if (userId == null || userId.trim().isEmpty() || perspective == null) {
            return Collections.emptyList();
        }
        return list(userId, perspective);
    }

    private String normalizeScope(String scopeCode) {
        return scopeCode == null ? "" : scopeCode.trim().toUpperCase(Locale.ROOT);
    }
}
