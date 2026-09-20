package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataQualityServiceImpl implements DataQualityService {
    private final BatchReadinessService readinessService;

    public DataQualityServiceImpl(BatchReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @Override
    public DataQualityReport get(String batchNo) {
        BatchReadiness readiness = readinessService.get(batchNo);
        int required = 0;
        int presentRequired = 0;
        int validRequired = 0;
        int issueCount = 0;
        List<String> missing = new ArrayList<String>();
        List<String> invalid = new ArrayList<String>();
        for (DatasetReadiness row : readiness.getDatasets()) {
            issueCount += row.getIssueCount();
            if (!row.isRequired()) continue;
            required++;
            if (!row.isPresent()) {
                missing.add(row.getDatasetCode());
                continue;
            }
            presentRequired++;
            if ("VALID".equalsIgnoreCase(row.getStatus()) && row.getIssueCount() == 0) {
                validRequired++;
            } else {
                invalid.add(row.getDatasetCode());
            }
        }
        String status = missing.isEmpty() && invalid.isEmpty() ? "READY" : "BLOCKED";
        return new DataQualityReport(batchNo, readiness.getPerspective(), status,
                readiness.getDatasets().size(), required, presentRequired, validRequired,
                issueCount, missing, invalid, readiness.getDatasets());
    }
}
