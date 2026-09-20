package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;

import java.io.InputStream;
import java.util.List;

public interface ConfigurationImportService {
    ConfigurationImportTask create(ConfigurationImportType type, String fileName,
                                   String checksum, String createdBy);
    ConfigurationImportTask get(String taskNo);
    List<ImportIssue> issues(String taskNo);
    ConfigurationImportTask upload(String taskNo, InputStream inputStream);
    ImportValidationResult validate(String taskNo, InputStream inputStream);
    ConfigurationImportTask move(String taskNo, ImportStatus status);
}
