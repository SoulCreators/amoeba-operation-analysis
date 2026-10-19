package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Application command for an export. It deliberately has no user identity;
 * identity must come from the service's authenticated-user argument.
 */
public final class ExportRequest {
    private final String requestKey;
    private final String batchNo;
    private final String resultVersion;
    private final AnalysisPerspective perspective;
    private final ExportFormat format;
    private final CalculationResultStatus resultStatus;
    private final Set<String> requestedScopes;

    public ExportRequest(String requestKey, String batchNo, String resultVersion,
                         AnalysisPerspective perspective, ExportFormat format) {
        this(requestKey, batchNo, resultVersion, perspective, format,
                CalculationResultStatus.PUBLISHED, Collections.<String>emptySet());
    }

    public ExportRequest(String requestKey, String batchNo, String resultVersion,
                         AnalysisPerspective perspective, ExportFormat format,
                         CalculationResultStatus resultStatus) {
        this(requestKey, batchNo, resultVersion, perspective, format,
                resultStatus, Collections.<String>emptySet());
    }

    public ExportRequest(String requestKey, String batchNo, String resultVersion,
                         AnalysisPerspective perspective, ExportFormat format,
                         CalculationResultStatus resultStatus, Set<String> requestedScopes) {
        this.requestKey = required(requestKey, "requestKey");
        this.batchNo = required(batchNo, "batchNo");
        this.resultVersion = required(resultVersion, "resultVersion");
        if (perspective == null) throw new IllegalArgumentException("perspective 不能为空");
        if (format == null) throw new IllegalArgumentException("format 不能为空");
        if (resultStatus == null) throw new IllegalArgumentException("resultStatus 不能为空");
        this.perspective = perspective;
        this.format = format;
        this.resultStatus = resultStatus;
        this.requestedScopes = immutableScopes(requestedScopes);
    }

    /** Factory for callers whose wire format is a format code rather than an enum. */
    public static ExportRequest fromFormatCode(String requestKey, String batchNo,
                                               String resultVersion,
                                               AnalysisPerspective perspective,
                                               String format,
                                               CalculationResultStatus resultStatus,
                                               Set<String> requestedScopes) {
        return new ExportRequest(requestKey, batchNo, resultVersion, perspective,
                ExportFormat.fromCode(format), resultStatus, requestedScopes);
    }

    public String getRequestKey() { return requestKey; }
    public String getBatchNo() { return batchNo; }
    public String getResultVersion() { return resultVersion; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public ExportFormat getFormat() { return format; }
    public CalculationResultStatus getResultStatus() { return resultStatus; }
    public Set<String> getRequestedScopes() { return requestedScopes; }

    /** Stable, application-owned name passed to the task port. */
    public String fileName() {
        return batchNo + "-" + resultVersion + "." + format.getExtension();
    }

    private static Set<String> immutableScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<String>(scopes));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
