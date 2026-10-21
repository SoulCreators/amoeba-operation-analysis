package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计事件读取授权策略。
 *
 * <p>本次新增：只复用现有权限模型中已明确存在的启用 ADMIN 角色，
 * 不把普通用户的查看、明细或导出范围推导为审计日志权限。</p>
 */
@Service
@Profile("db")
public final class AuditEventReadPolicy {
    private static final String ADMIN_ROLE = "ADMIN";

    private final PermissionService permissionService;

    // 本次新增：策略依赖现有应用权限服务，避免引入新的角色仓储或修改写权限模型。
    public AuditEventReadPolicy(PermissionService permissionService) {
        if (permissionService == null) {
            throw new IllegalArgumentException("permissionService must not be null");
        }
        this.permissionService = permissionService;
    }

    /** 本次新增：ADMIN 在任一已登记视角存在有效授权行时获得全量审计读取权。 */
    public boolean canReadAll(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        String normalizedUserId = userId.trim();
        for (AnalysisPerspective perspective : AnalysisPerspective.values()) {
            List<UserScope> scopes = permissionService.list(normalizedUserId, perspective);
            if (scopes == null) {
                continue;
            }
            for (UserScope scope : scopes) {
                if (isEnabledAdmin(scope)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isEnabledAdmin(UserScope scope) {
        return scope != null && scope.isEnabled()
                && scope.getRoleCode() != null
                && ADMIN_ROLE.equalsIgnoreCase(scope.getRoleCode().trim());
    }
}
