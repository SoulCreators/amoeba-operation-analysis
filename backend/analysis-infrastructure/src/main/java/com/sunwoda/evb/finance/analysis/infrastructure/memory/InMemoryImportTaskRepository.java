package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryImportTaskRepository implements ImportTaskRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentMap<String, ImportTask> store = new ConcurrentHashMap<String, ImportTask>();

    @Override
    public ImportTask save(ImportTask task) {
        if (task.getId() == null) {
            ImportTask saved = new ImportTask(sequence.getAndIncrement(), task.getTaskNo(),
                    task.getBatchNo(), task.getDatasetCode(), task.getFileName(),
                    task.getChecksum(), task.getCreatedBy(), task.getCreatedAt());
            saved.moveTo(task.getStatus(), task.getIssueCount());
            store.put(saved.getTaskNo(), saved);
            return saved;
        }
        store.put(task.getTaskNo(), task);
        return task;
    }

    @Override
    public Optional<ImportTask> findByTaskNo(String taskNo) {
        return Optional.ofNullable(store.get(taskNo));
    }

    @Override
    public boolean existsByBatchAndDataset(String batchNo, String datasetCode) {
        for (ImportTask task : store.values()) {
            if (batchNo.equals(task.getBatchNo()) && datasetCode.equals(task.getDatasetCode())) {
                return true;
            }
        }
        return false;
    }
}
