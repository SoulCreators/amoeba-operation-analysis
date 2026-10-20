package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetReadiness;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DATA-QUALITY-001 aggregation contract; it does not change readiness rules. */
class DataQualityServiceImplTest {
    private static final String BATCH_NO = "BATCH-DQ-001";

    @Test
    void aggregatesRequiredCountsIssuesAndRequiredDatasetLists() {
        DataQualityReport report = service(
                dataset("REQUIRED-MISSING", true, false, "", 2),
                dataset("REQUIRED-VALID", true, true, "VALID", 0),
                dataset("REQUIRED-INVALID", true, true, "INVALID", 3),
                dataset("OPTIONAL-INVALID", false, true, "INVALID", 5))
                .get(BATCH_NO);

        assertEquals(4, report.getTotalDatasetCount());
        assertEquals(3, report.getRequiredDatasetCount());
        assertEquals(2, report.getPresentRequiredDatasetCount());
        assertEquals(1, report.getValidRequiredDatasetCount());
        assertEquals(10, report.getIssueCount());
        assertEquals(Arrays.asList("REQUIRED-MISSING"), report.getMissingDatasetCodes());
        assertEquals(Arrays.asList("REQUIRED-INVALID"), report.getInvalidDatasetCodes());
        assertEquals("BLOCKED", report.getStatus());
    }

    @Test
    void optionalInvalidDatasetDoesNotBlockWhenRequiredDatasetIsValid() {
        DataQualityReport report = service(
                dataset("REQUIRED-VALID", true, true, "VALID", 0),
                dataset("OPTIONAL-INVALID", false, true, "INVALID", 4))
                .get(BATCH_NO);

        assertEquals("READY", report.getStatus());
        assertEquals(2, report.getTotalDatasetCount());
        assertEquals(1, report.getRequiredDatasetCount());
        assertEquals(1, report.getPresentRequiredDatasetCount());
        assertEquals(1, report.getValidRequiredDatasetCount());
        assertEquals(4, report.getIssueCount());
        assertTrue(report.getMissingDatasetCodes().isEmpty());
        assertTrue(report.getInvalidDatasetCodes().isEmpty());
    }

    @Test
    void requiredValidStatusWithIssuesIsInvalidAndBlocks() {
        DataQualityReport report = service(
                dataset("REQUIRED-WITH-ISSUES", true, true, "VALID", 1))
                .get(BATCH_NO);

        assertEquals("BLOCKED", report.getStatus());
        assertEquals(1, report.getRequiredDatasetCount());
        assertEquals(1, report.getPresentRequiredDatasetCount());
        assertEquals(0, report.getValidRequiredDatasetCount());
        assertEquals(1, report.getIssueCount());
        assertTrue(report.getMissingDatasetCodes().isEmpty());
        assertEquals(Arrays.asList("REQUIRED-WITH-ISSUES"), report.getInvalidDatasetCodes());
    }

    @Test
    void emptyDatasetListIsReadyWithZeroCountsAndEmptyLists() {
        DataQualityReport report = service().get(BATCH_NO);

        assertEquals("READY", report.getStatus());
        assertEquals(0, report.getTotalDatasetCount());
        assertEquals(0, report.getRequiredDatasetCount());
        assertEquals(0, report.getPresentRequiredDatasetCount());
        assertEquals(0, report.getValidRequiredDatasetCount());
        assertEquals(0, report.getIssueCount());
        assertTrue(report.getMissingDatasetCodes().isEmpty());
        assertTrue(report.getInvalidDatasetCodes().isEmpty());
        assertTrue(report.getDatasets().isEmpty());
    }

    private DataQualityService service(DatasetReadiness... datasets) {
        List<DatasetReadiness> rows = Arrays.asList(datasets);
        return new DataQualityServiceImpl(batchNo ->
                new BatchReadiness(BATCH_NO, AnalysisPerspective.BASE, false, rows));
    }

    private DatasetReadiness dataset(String code, boolean required, boolean present,
                                     String status, int issueCount) {
        return new DatasetReadiness(code, code, required, present, status, issueCount,
                present ? "TASK-" + code : null);
    }
}
