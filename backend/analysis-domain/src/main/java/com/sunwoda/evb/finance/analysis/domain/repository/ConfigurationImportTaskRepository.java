package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;

import java.util.Optional;

public interface ConfigurationImportTaskRepository {
    ConfigurationImportTask save(ConfigurationImportTask task);
    Optional<ConfigurationImportTask> findByTaskNo(String taskNo);
}
