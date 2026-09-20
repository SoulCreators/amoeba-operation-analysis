package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BatchReadinessServiceImpl implements BatchReadinessService {
    private final AnalysisBatchRepository batchRepository;
    private final ImportTaskRepository taskRepository;
    private final DatasetCatalog datasetCatalog;

    public BatchReadinessServiceImpl(AnalysisBatchRepository batchRepository,
                                     ImportTaskRepository taskRepository,
                                     DatasetCatalog datasetCatalog) {
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
        this.datasetCatalog = datasetCatalog;
    }

    @Override
    public BatchReadiness get(String batchNo) {
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        List<ImportTask> tasks = taskRepository.findByBatchNo(batchNo);
        List<DatasetReadiness> rows = new ArrayList<DatasetReadiness>();
        boolean ready = true;
        for (DatasetDefinition definition : datasetCatalog.list(batch.getPerspective())) {
            ImportTask task = find(tasks, definition.getCode());
            boolean present = task != null;
            String status = present ? task.getStatus().name() : "MISSING";
            int issueCount = present ? task.getIssueCount() : 0;
            rows.add(new DatasetReadiness(definition.getCode(), definition.getName(), definition.isRequired(),
                    present, status, issueCount, present ? task.getTaskNo() : null));
            if (definition.isRequired() && (!present || task.getStatus() != ImportStatus.VALID)) ready = false;
        }
        return new BatchReadiness(batchNo, batch.getPerspective(), ready, rows);
    }

    private ImportTask find(List<ImportTask> tasks, String datasetCode) {
        for (ImportTask task : tasks) if (datasetCode.equalsIgnoreCase(task.getDatasetCode())) return task;
        return null;
    }
}
