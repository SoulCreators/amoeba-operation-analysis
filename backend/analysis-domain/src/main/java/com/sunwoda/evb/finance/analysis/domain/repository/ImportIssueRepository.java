package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;

import java.util.List;

public interface ImportIssueRepository {
    void replace(String taskNo, List<ImportIssue> issues);
    List<ImportIssue> findByTaskNo(String taskNo);
}
