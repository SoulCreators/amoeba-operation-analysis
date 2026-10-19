package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactTraceQuery;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Application-layer permission gate for row-level fact provenance queries. */
@Service
public class TraceQueryServiceImpl {
    private final PermissionService permissionService;
    private final PnlFactTraceQuery sourceQuery;

    public TraceQueryServiceImpl(PermissionService permissionService,
                                 PnlFactTraceQuery sourceQuery) {
        if (permissionService == null) {
            throw new IllegalArgumentException("permissionService must not be null");
        }
        if (sourceQuery == null) {
            throw new IllegalArgumentException("sourceQuery must not be null");
        }
        this.permissionService = permissionService;
        this.sourceQuery = sourceQuery;
    }

    public List<PnlFactSource> findForUser(String batchNo, AnalysisPerspective perspective,
                                           String taskNo, String userId) {
        String requiredBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective must not be null");
        }
        String requiredTaskNo = required(taskNo, "taskNo");
        String requiredUserId = required(userId, "userId");

        // Resolve VIEW_DETAIL before touching the provenance repository.
        Set<String> detailScopes = permissionService.detailScopes(requiredUserId, perspective);
        if (detailScopes == null || detailScopes.isEmpty()) {
            throw new IllegalStateException("当前用户无追溯明细权限");
        }

        List<PnlFactSource> sources = sourceQuery.findForUser(
                requiredBatchNo, perspective, requiredTaskNo, requiredUserId);
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

        List<PnlFactSource> visible = new ArrayList<PnlFactSource>(sources.size());
        for (PnlFactSource source : sources) {
            if (source != null && containsScope(detailScopes, source.getScopeCode())) {
                visible.add(source);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    /** Explicit application-service spelling for callers that use query terminology. */
    public List<PnlFactSource> query(String batchNo, AnalysisPerspective perspective,
                                     String taskNo, String userId) {
        return findForUser(batchNo, perspective, taskNo, userId);
    }

    private boolean containsScope(Set<String> allowedScopes, String scopeCode) {
        if (scopeCode == null || scopeCode.trim().isEmpty()) {
            return false;
        }
        String normalizedScope = scopeCode.trim().toUpperCase(Locale.ROOT);
        for (String allowedScope : allowedScopes) {
            if (allowedScope != null
                    && ("ALL".equalsIgnoreCase(allowedScope.trim())
                    || normalizedScope.equals(allowedScope.trim().toUpperCase(Locale.ROOT)))) {
                return true;
            }
        }
        return false;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
