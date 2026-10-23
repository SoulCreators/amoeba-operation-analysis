package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.WorkbookDatasetDetector;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ImportTaskServiceImpl implements ImportTaskService {
    private final AnalysisBatchRepository batchRepository;
    private final ImportTaskRepository taskRepository;
    private final DatasetCatalog datasetCatalog;
    private final TemplateValidator templateValidator;
    private final ImportIssueRepository issueRepository;
    private final ImportFileRepository fileRepository;
    @Autowired(required = false)
    private PnlFactTaskReader factReader;
    @Autowired(required = false)
    private PnlFactWriter factWriter;
    @Autowired(required = false)
    private PnlFactSourceReader sourceReader;
    @Autowired(required = false)
    private PnlFactSourceWriter sourceWriter;
    @Autowired(required = false)
    private WorkbookDatasetDetector workbookDatasetDetector;

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
        return newTask(batchNo, datasetCode, fileName, checksum, createdBy);
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
    @Transactional
    public ImportValidationResult validate(String taskNo, InputStream inputStream) {
        if (inputStream == null) throw new IllegalArgumentException("导入文件不能为空");
        return validateContent(get(taskNo), readBytes(inputStream));
    }

    @Override
    @Transactional
    public List<ImportTask> importWorkbook(String batchNo, String fileName,
                                           InputStream inputStream, String createdBy) {
        requireText(batchNo, "batchNo");
        requireText(fileName, "fileName");
        requireText(createdBy, "createdBy");
        if (inputStream == null) throw new IllegalArgumentException("导入文件不能为空");
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        if (workbookDatasetDetector == null) {
            throw new IllegalStateException("整本导入未配置工作簿数据集识别器");
        }
        byte[] content = readBytes(inputStream);
        List<String> candidates = new ArrayList<String>();
        for (DatasetDefinition definition : datasetCatalog.list(batch.getPerspective())) {
            candidates.add(definition.getCode());
        }
        Set<String> present = workbookDatasetDetector.detectPresentDatasets(content, candidates);
        List<ImportTask> imported = new ArrayList<ImportTask>();
        for (String datasetCode : candidates) {
            if (!present.contains(datasetCode)) continue;
            if (taskRepository.existsByBatchAndDataset(batchNo, datasetCode)) continue;
            ImportTask task = newTask(batchNo, datasetCode, fileName, null, createdBy);
            validateContent(task, content);
            imported.add(task);
        }
        return imported;
    }

    private ImportValidationResult validateContent(ImportTask task, byte[] content) {
        String taskNo = task.getTaskNo();
        AnalysisBatch batch = batchRepository.findByBatchNo(task.getBatchNo())
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + task.getBatchNo()));
        task.moveTo(ImportStatus.VALIDATING, 0);
        taskRepository.save(task);
        Optional<ImportFileSnapshot> previousFile = fileRepository.snapshot(taskNo);
        if (previousFile == null) previousFile = Optional.empty();
        try {
            fileRepository.save(taskNo, task.getFileName(), content);
            ImportValidationResult result = templateValidator.validate(taskNo,
                    datasetCatalog.require(task.getDatasetCode(), batch.getPerspective()),
                    new ByteArrayInputStream(content));
            issueRepository.replace(taskNo, result.getIssues());
            task.moveTo(result.getStatus(), result.getIssues().size());
            taskRepository.save(task);
            if (result.getStatus() == ImportStatus.VALID && factWriter != null) {
                if (factReader == null || sourceReader == null || sourceWriter == null) {
                    throw new IllegalStateException(
                            "事实写入已配置，但事实读取器、来源读取器和来源写入器必须成套配置");
                }
                List<com.sunwoda.evb.finance.analysis.domain.model.PnlFact> facts =
                        factReader.read(batch, task);
                List<com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource> sources =
                        sourceReader.readSources(batch, task);
                factWriter.replace(batch, task, facts);
                sourceWriter.replace(batch, task, sources);
            }
            return result;
        } catch (RuntimeException ex) {
            restoreAfterFailure(taskNo, previousFile, ex);
            throw ex;
        } catch (Error ex) {
            restoreAfterFailure(taskNo, previousFile, ex);
            throw ex;
        }
    }

    private ImportTask newTask(String batchNo, String datasetCode, String fileName,
                               String checksum, String createdBy) {
        String taskNo = "IMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return taskRepository.save(new ImportTask(null, taskNo, batchNo, datasetCode,
                fileName, checksum, createdBy, LocalDateTime.now()));
    }

    private void restoreAfterFailure(String taskNo, Optional<ImportFileSnapshot> previousFile,
                                     Throwable originalFailure) {
        try {
            fileRepository.restore(taskNo, previousFile.orElse(null));
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
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
