package com.sunwoda.evb.finance.analysis.application.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable export authorization result.
 *
 * <p>Summary and detail scopes are deliberately separate. A caller may export
 * an authorized summary even when the same scope has no detail permission.</p>
 */
public final class ExportPermission {
    private final boolean allowed;
    private final Set<String> summaryScopes;
    private final Set<String> detailScopes;
    private final String denialReason;

    private ExportPermission(boolean allowed, Set<String> summaryScopes,
                             Set<String> detailScopes, String denialReason) {
        this.allowed = allowed;
        this.summaryScopes = immutableScopes(summaryScopes);
        this.detailScopes = immutableScopes(detailScopes);
        this.denialReason = denialReason;
    }

    public static ExportPermission allowed(Set<String> summaryScopes, Set<String> detailScopes) {
        return new ExportPermission(true, summaryScopes, detailScopes, null);
    }

    public static ExportPermission denied(String reason) {
        return new ExportPermission(false, Collections.<String>emptySet(),
                Collections.<String>emptySet(), reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public boolean isSummaryAllowed() {
        return allowed && !summaryScopes.isEmpty();
    }

    public boolean isDetailAllowed() {
        return allowed && !detailScopes.isEmpty();
    }

    public Set<String> getSummaryScopes() {
        return summaryScopes;
    }

    public Set<String> getDetailScopes() {
        return detailScopes;
    }

    public String getDenialReason() {
        return denialReason;
    }

    public boolean canExportSummary(String scopeCode) {
        return isSummaryAllowed() && contains(summaryScopes, scopeCode);
    }

    public boolean canExportDetail(String scopeCode) {
        return isDetailAllowed() && contains(detailScopes, scopeCode);
    }

    private static Set<String> immutableScopes(Set<String> scopes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        if (scopes != null) {
            for (String scope : scopes) {
                if (scope != null && !scope.trim().isEmpty()) {
                    normalized.add(scope.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static boolean contains(Set<String> scopes, String scopeCode) {
        if (scopeCode == null || scopeCode.trim().isEmpty()) {
            return false;
        }
        String normalized = scopeCode.trim().toUpperCase(Locale.ROOT);
        return scopes.contains("ALL") || scopes.contains(normalized);
    }
}
