package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryConfigurationImportTaskRepository implements ConfigurationImportTaskRepository {
    private final AtomicLong sequence = new AtomicLong(1L);
    private final ConcurrentMap<String, ConfigurationImportTask> store =
            new ConcurrentHashMap<String, ConfigurationImportTask>();

    @Override
    public ConfigurationImportTask save(ConfigurationImportTask task) {
        if (task.getId() == null) {
            ConfigurationImportTask saved = new ConfigurationImportTask(sequence.getAndIncrement(),
                    task.getTaskNo(), task.getType(), task.getFileName(), task.getChecksum(),
                    task.getCreatedBy(), task.getCreatedAt());
            saved.moveTo(task.getStatus());
            store.put(saved.getTaskNo(), saved);
            return saved;
        }
        store.put(task.getTaskNo(), task);
        return task;
    }

    @Override
    public Optional<ConfigurationImportTask> findByTaskNo(String taskNo) {
        return Optional.ofNullable(store.get(taskNo));
    }
}
