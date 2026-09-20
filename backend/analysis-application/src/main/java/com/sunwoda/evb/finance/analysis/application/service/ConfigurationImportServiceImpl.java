package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class ConfigurationImportServiceImpl implements ConfigurationImportService {
    private final ConfigurationImportTaskRepository repository;
    private final ImportFileRepository fileRepository;

    public ConfigurationImportServiceImpl(ConfigurationImportTaskRepository repository,
                                         ImportFileRepository fileRepository) {
        this.repository = repository;
        this.fileRepository = fileRepository;
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
    public ConfigurationImportTask upload(String taskNo, InputStream inputStream) {
        if (inputStream == null) throw new IllegalArgumentException("配置文件不能为空");
        ConfigurationImportTask task = get(taskNo);
        byte[] content = readBytes(inputStream);
        fileRepository.save(taskNo, task.getFileName(), content);
        task.moveTo(ImportStatus.VALIDATING);
        return repository.save(task);
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

    private byte[] readBytes(InputStream inputStream) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
            if (output.size() == 0) throw new IllegalArgumentException("配置文件不能为空");
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("配置文件读取失败: " + ex.getMessage(), ex);
        }
    }
}
