package com.sunwoda.evb.finance.analysis.domain.repository;

/**
 * Import issue storage dedicated to configuration import tasks.
 *
 * <p>Configuration imports have their own task table and therefore must not
 * reuse the monthly fact-import issue foreign key.</p>
 */
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;

import java.util.List;

public interface ConfigurationImportIssueRepository {

    void replace(String taskNo, List<ImportIssue> issues);

    List<ImportIssue> findByTaskNo(String taskNo);
}
