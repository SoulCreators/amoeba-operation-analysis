package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;

public interface ConfigurationImportService {
    ConfigurationImportTask create(ConfigurationImportType type, String fileName,
                                   String checksum, String createdBy);
    ConfigurationImportTask get(String taskNo);
    ConfigurationImportTask move(String taskNo, ImportStatus status);
}
