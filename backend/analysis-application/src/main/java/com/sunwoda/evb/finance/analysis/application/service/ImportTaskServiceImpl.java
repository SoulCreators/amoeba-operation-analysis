package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.List;

@Service
public class ImportTaskServiceImpl implements ImportTaskService {
    private final AnalysisBatchRepository batchRepository;
    private final ImportTaskRepository taskRepository;
    private final DatasetCatalog datasetCatalog;
    private final TemplateValidator templateValidator;
    private final ImportIssueRepository issueRepository;
    private final ImportFileRepository fileRepository;

    public ImportTaskServiceImpl(AnalysisBatchRepository batchRepository,
                                 ImportTaskRepository taskRepository,
                                 DatasetCatalog datasetCatalog,
                                 TemplateValidator templateValidator,
                                 ImportIssueRepository issueRepository,
                                 ImportFileRepository fileRepository) {
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.datasetCatalog = datasetCatalog;
        this.templateValidator = templateValidator;
        this.issueRepository = issueRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public ImportTask create(String batchNo, String datasetCode, String fileName,
                             String checksum, String createdBy) {
        requireText(batchNo, "batchNo");
        requireText(datasetCode, "datasetCode");
        requireText(fileName, "fileName");
        requireText(createdBy, "createdBy");
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        datasetCatalog.require(datasetCode, batch.getPerspective());
        if (taskRepository.existsByBatchAndDataset(batchNo, datasetCode)) {
            throw new IllegalStateException("该批次已存在同类数据集导入任务: " + datasetCode);
        }
        String taskNo = "IMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return taskRepository.save(new ImportTask(null, taskNo, batchNo, datasetCode,
                fileName, checksum, createdBy, LocalDateTime.now()));
    }

    @Override
    public ImportTask get(String taskNo) {
        return taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new IllegalArgumentException("导入任务不存在: " + taskNo));
    }

    @Override
    public ImportTask move(String taskNo, ImportStatus status, int issueCount) {
        ImportTask task = get(taskNo);
        task.moveTo(status, issueCount);
        return taskRepository.save(task);
    }

    @Override
    public ImportValidationResult validate(String taskNo, InputStream inputStream) {
        if (inputStream == null) throw new IllegalArgumentException("导入文件不能为空");
        ImportTask task = get(taskNo);
        AnalysisBatch batch = batchRepository.findByBatchNo(task.getBatchNo())
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + task.getBatchNo()));
        task.moveTo(ImportStatus.VALIDATING, 0);
        taskRepository.save(task);
        byte[] content = readBytes(inputStream);
        fileRepository.save(taskNo, task.getFileName(), content);
        ImportValidationResult result = templateValidator.validate(taskNo,
                datasetCatalog.require(task.getDatasetCode(), batch.getPerspective()),
                new java.io.ByteArrayInputStream(content));
        issueRepository.replace(taskNo, result.getIssues());
        task.moveTo(result.getStatus(), result.getIssues().size());
        taskRepository.save(task);
        return result;
    }

    private byte[] readBytes(InputStream inputStream) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
            if (output.size() == 0) throw new IllegalArgumentException("导入文件不能为空");
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("导入文件读取失败: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<ImportIssue> issues(String taskNo) {
        get(taskNo);
        return issueRepository.findByTaskNo(taskNo);
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}
