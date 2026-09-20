package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.List;

public class ConfigurationApplyResult {
    private final int appliedCount;
    private final List<ImportIssue> issues;

    public ConfigurationApplyResult(int appliedCount, List<ImportIssue> issues) {
        this.appliedCount = appliedCount;
        this.issues = Collections.unmodifiableList(issues);
    }

    public int getAppliedCount() { return appliedCount; }
    public List<ImportIssue> getIssues() { return issues; }
}
