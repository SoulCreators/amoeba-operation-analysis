package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;

public interface ImportTaskService {
    ImportTask create(String batchNo, String datasetCode, String fileName,
                      String checksum, String createdBy);
    ImportTask get(String taskNo);
    ImportTask move(String taskNo, ImportStatus status, int issueCount);
}
