package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable input snapshot captured when an export task is created.
 *
 * <p>The scope sets are the already-authorized sets returned by the export
 * permission policy.  They are copied here so later permission changes or
 * caller mutation cannot change what a worker is allowed to render.</p>
 */
public final class ExportTaskSnapshot {
    private final String batchNo;
    private final String resultVersion;
    private final AnalysisPerspective perspective;
    private final CalculationResultStatus resultStatus;
    private final ExportFormat format;
    private final Set<String> summaryScopes;
    private final Set<String> detailScopes;

    public ExportTaskSnapshot(String batchNo, String resultVersion,
                              AnalysisPerspective perspective,
                              CalculationResultStatus resultStatus,
                              ExportFormat format,
                              Set<String> summaryScopes,
                              Set<String> detailScopes) {
        this.batchNo = required(batchNo, "batchNo");
        this.resultVersion = required(resultVersion, "resultVersion");
        if (perspective == null) throw new IllegalArgumentException("perspective 不能为空");
        if (resultStatus == null) throw new IllegalArgumentException("resultStatus 不能为空");
        if (format == null) throw new IllegalArgumentException("format 不能为空");
        this.perspective = perspective;
        this.resultStatus = resultStatus;
        this.format = format;
        this.summaryScopes = immutableScopes(summaryScopes);
        this.detailScopes = immutableScopes(detailScopes);
    }

    public static ExportTaskSnapshot from(ExportRequest request,
                                          Set<String> summaryScopes,
                                          Set<String> detailScopes) {
        if (request == null) throw new IllegalArgumentException("request 不能为空");
        return new ExportTaskSnapshot(request.getBatchNo(), request.getResultVersion(),
                request.getPerspective(), request.getResultStatus(), request.getFormat(),
                summaryScopes, detailScopes);
    }

    public String getBatchNo() { return batchNo; }
    public String getResultVersion() { return resultVersion; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public CalculationResultStatus getResultStatus() { return resultStatus; }
    public ExportFormat getFormat() { return format; }
    public Set<String> getSummaryScopes() { return summaryScopes; }
    public Set<String> getDetailScopes() { return detailScopes; }

    public String fileName() {
        return batchNo + "-" + resultVersion + "." + format.getExtension();
    }

    public String contentType() {
        return format.getContentType();
    }

    private static Set<String> immutableScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String scope : scopes) {
            if (scope != null && !scope.trim().isEmpty()) {
                normalized.add(scope.trim().toUpperCase(Locale.ROOT));
            }
        }
        return normalized.isEmpty()
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(normalized);
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
