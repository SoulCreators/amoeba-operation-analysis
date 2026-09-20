package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import java.util.Collections;
import java.util.List;

@Service
public class ConfigurationImportServiceImpl implements ConfigurationImportService {
    private final ConfigurationImportTaskRepository repository;
    private final ImportFileRepository fileRepository;
    @Autowired(required = false)
    private ConfigurationTemplateValidator validator;
    @Autowired(required = false)
    private ImportIssueRepository issueRepository;
    @Autowired(required = false)
    private ConfigurationImportApplier applier;

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
    public List<ImportIssue> issues(String taskNo) {
        get(taskNo);
        return issueRepository == null ? Collections.<ImportIssue>emptyList()
                : issueRepository.findByTaskNo(taskNo);
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
    public ImportValidationResult validate(String taskNo, InputStream inputStream) {
        if (inputStream == null) throw new IllegalArgumentException("配置文件不能为空");
        if (validator == null) throw new IllegalStateException("当前环境未配置配置文件校验器");
        ConfigurationImportTask task = get(taskNo);
        byte[] content = readBytes(inputStream);
        fileRepository.save(taskNo, task.getFileName(), content);
        ImportValidationResult result = validator.validate(taskNo, task.getType(),
                new java.io.ByteArrayInputStream(content));
        if (result.getStatus() == ImportStatus.VALID && applier != null) {
            ConfigurationApplyResult applied = applier.apply(task, new java.io.ByteArrayInputStream(content));
            if (!applied.getIssues().isEmpty()) {
                java.util.List<ImportIssue> issues = new java.util.ArrayList<ImportIssue>(result.getIssues());
                issues.addAll(applied.getIssues());
                result = new ImportValidationResult(taskNo, ImportStatus.INVALID, result.getRowCount(),
                        result.getHeaders(), issues);
            }
        }
        if (issueRepository != null) issueRepository.replace(taskNo, result.getIssues());
        task.moveTo(result.getStatus());
        repository.save(task);
        return result;
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
