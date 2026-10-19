package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Application-layer authorization policy for result exports.
 *
 * <p>The policy is intentionally independent from HTTP, file formats,
 * persistence and task scheduling. It reuses the existing permission service
 * as the source of truth and returns the already-clipped summary/detail scope
 * sets for an eventual export task.</p>
 */
@Service
public final class ExportPermissionPolicy {
    private final PermissionService permissionService;

    public ExportPermissionPolicy(PermissionService permissionService) {
        if (permissionService == null) {
            throw new IllegalArgumentException("permissionService must not be null");
        }
        this.permissionService = permissionService;
    }

    /**
     * Resolves all exportable scopes for a result.
     */
    public ExportPermission authorize(String userId, AnalysisPerspective perspective,
                                      CalculationResultStatus resultStatus) {
        return authorize(userId, perspective, resultStatus, Collections.<String>emptySet());
    }

    /**
     * Resolves an optional requested-scope subset. An empty subset means the
     * caller did not narrow the export and the user's export scopes are used.
     */
    public ExportPermission authorize(String userId, AnalysisPerspective perspective,
                                      CalculationResultStatus resultStatus,
                                      Set<String> requestedScopes) {
        if (userId == null || userId.trim().isEmpty()) {
            return ExportPermission.denied("userId must not be blank");
        }
        if (perspective == null) {
            return ExportPermission.denied("perspective must not be null");
        }
        if (resultStatus == null) {
            return ExportPermission.denied("resultStatus must not be null");
        }

        String normalizedUser = userId.trim();
        if (resultStatus != CalculationResultStatus.PUBLISHED
                && !permissionService.canViewDraft(normalizedUser, perspective)) {
            return ExportPermission.denied("unpublished result is restricted to finance preview");
        }

        Set<String> exportScopes = permissionService.exportScopes(normalizedUser, perspective);
        if (exportScopes == null || exportScopes.isEmpty()) {
            return ExportPermission.denied("current user has no export scope");
        }

        Set<String> candidates = candidateScopes(exportScopes, requestedScopes);
        Set<String> summaryScopes = new LinkedHashSet<String>();
        Set<String> detailScopes = new LinkedHashSet<String>();
        for (String scope : candidates) {
            // exportScopes is the coarse grant set; canExport is the exact
            // scope check and remains the final authorization decision.
            if (!permissionService.canExport(normalizedUser, perspective, scope)) {
                continue;
            }
            summaryScopes.add(scope);
            // Detail is an independent, stricter slice. It never removes the
            // summary grant when the user can export but cannot view detail.
            if (permissionService.canViewDetail(normalizedUser, perspective, scope)) {
                detailScopes.add(scope);
            }
        }

        if (summaryScopes.isEmpty()) {
            return ExportPermission.denied("requested scopes are not exportable");
        }
        return ExportPermission.allowed(summaryScopes, detailScopes);
    }

    /** Explicit alias for callers that use decision terminology. */
    public ExportPermission evaluate(String userId, AnalysisPerspective perspective,
                                     CalculationResultStatus resultStatus,
                                     Set<String> requestedScopes) {
        return authorize(userId, perspective, resultStatus, requestedScopes);
    }

    public boolean canExportSummary(String userId, AnalysisPerspective perspective,
                                    CalculationResultStatus resultStatus, String scopeCode) {
        return authorize(userId, perspective, resultStatus,
                Collections.singleton(scopeCode)).canExportSummary(scopeCode);
    }

    public boolean canExportDetail(String userId, AnalysisPerspective perspective,
                                   CalculationResultStatus resultStatus, String scopeCode) {
        return authorize(userId, perspective, resultStatus,
                Collections.singleton(scopeCode)).canExportDetail(scopeCode);
    }

    private Set<String> candidateScopes(Set<String> exportScopes, Set<String> requestedScopes) {
        Set<String> normalizedRequested = normalize(requestedScopes);
        if (!normalizedRequested.isEmpty()) {
            return normalizedRequested;
        }
        return normalize(exportScopes);
    }

    private Set<String> normalize(Set<String> scopes) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (scopes == null) {
            return normalized;
        }
        for (String scope : scopes) {
            if (scope != null && !scope.trim().isEmpty()) {
                normalized.add(scope.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }
}
