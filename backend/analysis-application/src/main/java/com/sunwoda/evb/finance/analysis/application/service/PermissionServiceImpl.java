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
        for (UserScope scope : safeList(userId, perspective)) {
            if (!scope.isEnabled()) continue;
            String role = scope.getRoleCode().toUpperCase(Locale.ROOT);
            if (role.contains("FINANCE")) return true;
        }
        return false;
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
        return canViewDraft(userId, perspective);
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
        for (UserScope scope : safeList(userId, perspective)) {
            if (scope.matchesScope(scopeCode) && scope.allows(action)) return true;
        }
        return false;
    }

    private Set<String> scopesFor(String userId, AnalysisPerspective perspective,
                                  PermissionAction action) {
        Set<String> result = new LinkedHashSet<String>();
        for (UserScope scope : safeList(userId, perspective)) {
            if (scope.allows(action)) result.add(normalizeScope(scope.getScopeCode()));
        }
        return result;
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
