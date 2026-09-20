package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;

import java.io.InputStream;
import java.util.List;

public interface ImportTaskService {
    ImportTask create(String batchNo, String datasetCode, String fileName,
                      String checksum, String createdBy);
    ImportTask get(String taskNo);
    ImportTask move(String taskNo, ImportStatus status, int issueCount);
    ImportValidationResult validate(String taskNo, InputStream inputStream);
    List<ImportIssue> issues(String taskNo);
}
