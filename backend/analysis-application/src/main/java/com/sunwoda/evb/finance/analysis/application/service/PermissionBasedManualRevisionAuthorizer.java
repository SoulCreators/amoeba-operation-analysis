package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.CalculationVersionScopeResolver;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 人工修订生产授权器。
 *
 * <p>本次新增：仅在 db profile 下显式注入权限服务和生产 scope 解析端口；
 * 先解析实际业务范围，再调用总部财经的范围级人工修订权限，ADMIN 只读不会获得写权限。</p>
 */
@Component
// 本次修改：授权器与生产 scope 解析器保持同一 Profile，local 不注册半套人工修订依赖。
@Profile("db")
public class PermissionBasedManualRevisionAuthorizer implements ManualRevisionAuthorizer {
    private final PermissionService permissionService;
    private final CalculationVersionScopeResolver scopeResolver;

    public PermissionBasedManualRevisionAuthorizer(PermissionService permissionService,
                                                   CalculationVersionScopeResolver scopeResolver) {
        if (permissionService == null || scopeResolver == null) {
            throw new IllegalArgumentException("人工修订授权依赖不能为空");
        }
        this.permissionService = permissionService;
        this.scopeResolver = scopeResolver;
    }

    @Override
    public void assertAuthorized(String reviser, CalculationVersion sourceVersion) {
        if (sourceVersion == null) {
            throw new IllegalArgumentException("来源计算版本不能为空");
        }
        Set<String> scopeCodes = scopeResolver.resolve(sourceVersion);
        if (scopeCodes == null || scopeCodes.isEmpty()) {
            // 本次新增：无法证明实际 scope 时失败关闭，避免把视角级权限误当作范围级授权。
            throw new IllegalStateException("来源计算版本缺少可审计的授权范围");
        }
        if (!permissionService.canManualRevise(reviser, sourceVersion.getPerspective(), scopeCodes)) {
            throw new IllegalStateException("当前用户无来源计算版本涉及范围的人工修订权限");
        }
    }
}
