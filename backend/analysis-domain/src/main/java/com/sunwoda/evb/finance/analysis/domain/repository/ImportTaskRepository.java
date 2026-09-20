package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;

import java.util.Optional;
import java.util.List;

public interface ImportTaskRepository {
    ImportTask save(ImportTask task);
    Optional<ImportTask> findByTaskNo(String taskNo);
    boolean existsByBatchAndDataset(String batchNo, String datasetCode);
    List<ImportTask> findByBatchNo(String batchNo);
}
