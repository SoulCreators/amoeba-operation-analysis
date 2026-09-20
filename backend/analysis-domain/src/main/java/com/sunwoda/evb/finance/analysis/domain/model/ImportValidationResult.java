package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

public class ImportValidationResult {
    private final String taskNo;
    private final ImportStatus status;
    private final int rowCount;
    private final List<String> headers;
    private final List<ImportIssue> issues;

    public ImportValidationResult(String taskNo, ImportStatus status, int rowCount,
                                  List<String> headers, List<ImportIssue> issues) {
        this.taskNo = taskNo;
        this.status = status;
        this.rowCount = rowCount;
        this.headers = Collections.unmodifiableList(headers);
        this.issues = Collections.unmodifiableList(issues);
    }

    public String getTaskNo() { return taskNo; }
    public ImportStatus getStatus() { return status; }
    public int getRowCount() { return rowCount; }
    public List<String> getHeaders() { return headers; }
    public List<ImportIssue> getIssues() { return issues; }
}
