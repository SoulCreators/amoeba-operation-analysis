package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;

import java.util.Optional;

public interface ImportTaskRepository {
    ImportTask save(ImportTask task);
    Optional<ImportTask> findByTaskNo(String taskNo);
    boolean existsByBatchAndDataset(String batchNo, String datasetCode);
}
