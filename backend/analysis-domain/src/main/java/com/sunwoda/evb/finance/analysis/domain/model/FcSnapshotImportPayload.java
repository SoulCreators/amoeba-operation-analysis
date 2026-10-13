package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** FC 快照模板解析结果；存在校验问题时禁止部分写入。 */
public class FcSnapshotImportPayload {
    private final String taskNo;
    private final int rowCount;
    private final List<String> headers;
    private final List<FcSnapshot> snapshots;
    private final List<ImportIssue> issues;

    public FcSnapshotImportPayload(String taskNo, int rowCount, List<String> headers,
                                   List<FcSnapshot> snapshots, List<ImportIssue> issues) {
        this.taskNo = taskNo;
        this.rowCount = rowCount;
        this.headers = Collections.unmodifiableList(new ArrayList<String>(headers));
        this.snapshots = Collections.unmodifiableList(new ArrayList<FcSnapshot>(snapshots));
        this.issues = Collections.unmodifiableList(new ArrayList<ImportIssue>(issues));
    }

    public String getTaskNo() { return taskNo; }
    public int getRowCount() { return rowCount; }
    public List<String> getHeaders() { return headers; }
    public List<FcSnapshot> getSnapshots() { return snapshots; }
    public List<ImportIssue> getIssues() { return issues; }
}
