package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.CalculationVersionScopeResolver;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * DATA-004-TECH-003-REVISION-003 应用授权测试。
 *
 * <p>本次新增测试：确认人工修订必须使用来源版本实际 scope；scope 无法解析时失败关闭，
 * 不允许把 ADMIN 的全量读取能力或总部财经的视角级权限升级为写权限。</p>
 */
class PermissionBasedManualRevisionAuthorizerTest {

    @Test
    void authorizedRevisionUsesActualScopes() {
        PermissionService permissionService = mock(PermissionService.class);
        CalculationVersionScopeResolver scopeResolver = mock(CalculationVersionScopeResolver.class);
        CalculationVersion source = sourceVersion();
        Set<String> scopes = new LinkedHashSet<String>(Arrays.asList("SCOPE-A", "SCOPE-B"));
        when(scopeResolver.resolve(source)).thenReturn(scopes);
        when(permissionService.canManualRevise("HQ-FINANCE-001", AnalysisPerspective.BASE, scopes))
                .thenReturn(true);

        new PermissionBasedManualRevisionAuthorizer(permissionService, scopeResolver)
                .assertAuthorized("HQ-FINANCE-001", source);

        verify(permissionService).canManualRevise("HQ-FINANCE-001", AnalysisPerspective.BASE, scopes);
    }

    @Test
    void unresolvedScopesFailClosed() {
        PermissionService permissionService = mock(PermissionService.class);
        CalculationVersionScopeResolver scopeResolver = mock(CalculationVersionScopeResolver.class);
        when(scopeResolver.resolve(sourceVersion())).thenReturn(Collections.<String>emptySet());

        assertThrows(IllegalStateException.class, () ->
                new PermissionBasedManualRevisionAuthorizer(permissionService, scopeResolver)
                        .assertAuthorized("HQ-FINANCE-001", sourceVersion()));
    }

    @Test
    void adminReadOnlyCannotBecomeRevisionWrite() {
        PermissionService permissionService = mock(PermissionService.class);
        CalculationVersionScopeResolver scopeResolver = mock(CalculationVersionScopeResolver.class);
        CalculationVersion source = sourceVersion();
        Set<String> scopes = Collections.singleton("SCOPE-A");
        when(scopeResolver.resolve(source)).thenReturn(scopes);
        when(permissionService.canManualRevise("ADMIN-001", AnalysisPerspective.BASE, scopes))
                .thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                new PermissionBasedManualRevisionAuthorizer(permissionService, scopeResolver)
                        .assertAuthorized("ADMIN-001", source));
    }

    private CalculationVersion sourceVersion() {
        return new CalculationVersion(
                "CALC-V-001", "BATCH-001", AnalysisPerspective.BASE, "V001",
                "JOB-001", "SNAPSHOT-001", "SOURCE-HASH", "CONFIG-HASH", "INPUT-FINGERPRINT",
                CalculationVersionStatus.PUBLISHED, true, null, "TRACE-001", "calculator",
                LocalDateTime.of(2026, 10, 21, 10, 0), "confirmer",
                LocalDateTime.of(2026, 10, 21, 10, 1), "publisher",
                LocalDateTime.of(2026, 10, 21, 10, 2));
    }
}
