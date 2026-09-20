package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConfigurationImportServiceImpl implements ConfigurationImportService {
    private final ConfigurationImportTaskRepository repository;

    public ConfigurationImportServiceImpl(ConfigurationImportTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public ConfigurationImportTask create(ConfigurationImportType type, String fileName,
                                          String checksum, String createdBy) {
        if (type == null) throw new IllegalArgumentException("配置导入类型不能为空");
        require(fileName, "fileName");
        require(createdBy, "createdBy");
        String taskNo = "CFG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return repository.save(new ConfigurationImportTask(null, taskNo, type, fileName,
                checksum, createdBy, LocalDateTime.now()));
    }

    @Override
    public ConfigurationImportTask get(String taskNo) {
        return repository.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("配置导入任务不存在: " + taskNo));
    }

    @Override
    public ConfigurationImportTask move(String taskNo, ImportStatus status) {
        ConfigurationImportTask task = get(taskNo);
        task.moveTo(status);
        return repository.save(task);
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}
