package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessServiceImpl;
import com.sunwoda.evb.finance.analysis.application.service.DataQualityServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryDatasetCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL contract for batch data-quality readiness and statistics.
 *
 * <p>This contract is intentionally not skippable: it must be run with the
 * configured PostgreSQL connection so a skipped test cannot be reported as
 * evidence of database behavior.</p>
 */
class DataQualityPostgresContractTest {
    private static final String[] REQUIRED_DATASETS = {
            "BASE_ACT_INC_COST", "BASE_BUD_INC_COST", "BASE_IDLE",
            "BASE_L3_EXP", "BASE_LAB_MFG_EXP"
    };

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAnalysisBatchRepository batches;
    private static JdbcImportTaskRepository tasks;

    private String batchNo;
    private BatchReadinessServiceImpl readiness;
    private DataQualityServiceImpl quality;

    @BeforeAll
    static void connectToConfiguredPostgresAndVerifySchema() {
        String url = requiredEnvironment("AMOEBA_DB_URL");
        dataSource = new DriverManagerDataSource(
                url, requiredEnvironment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SELECT perspective, status FROM meta.analysis_batch LIMIT 0");
        jdbc.execute("SELECT status, issue_count FROM flow.import_task LIMIT 0");
        batches = new JdbcAnalysisBatchRepository(dataSource);
        tasks = new JdbcImportTaskRepository(dataSource);
    }

    @BeforeEach
    void createRandomBatch() {
        batchNo = "DATA-QUALITY-DB-" + UUID.randomUUID().toString().replace("-", "");
        AnalysisBatch batch = new AnalysisBatch(null, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "TEST-DATA-QUALITY-001-DB", LocalDateTime.now());
        batches.save(batch);
        readiness = new BatchReadinessServiceImpl(batches, tasks, new InMemoryDatasetCatalog());
        quality = new DataQualityServiceImpl(readiness);
    }

    @AfterEach
    void removeRandomBatch() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM flow.import_task WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meta.analysis_batch WHERE batch_no = ?",
                Integer.class, batchNo).intValue());
        batchNo = null;
    }

    @Test
    void missingRequiredDatasetBlocksAndReportsRequiredPresenceStatistics() {
        persistRequiredTasksExcept("BASE_LAB_MFG_EXP");

        BatchReadiness batchReadiness = readiness.get(batchNo);
        DataQualityReport report = quality.get(batchNo);

        assertFalse(batchReadiness.isReadyForCalculation());
        assertEquals("BLOCKED", report.getStatus());
        assertEquals(8, report.getTotalDatasetCount());
        assertEquals(5, report.getRequiredDatasetCount());
        assertEquals(4, report.getPresentRequiredDatasetCount());
        assertEquals(4, report.getValidRequiredDatasetCount());
        assertEquals(0, report.getIssueCount());
        assertEquals(1, report.getMissingDatasetCodes().size());
        assertEquals("BASE_LAB_MFG_EXP", report.getMissingDatasetCodes().get(0));
        assertTrue(report.getInvalidDatasetCodes().isEmpty());
    }

    @Test
    void invalidRequiredDatasetBlocksAndCountsItsIssues() {
        persistRequiredTasksExcept("BASE_LAB_MFG_EXP");
        persistTask("BASE_LAB_MFG_EXP", ImportStatus.INVALID, 2);

        BatchReadiness batchReadiness = readiness.get(batchNo);
        DataQualityReport report = quality.get(batchNo);

        assertFalse(batchReadiness.isReadyForCalculation());
        assertEquals("BLOCKED", report.getStatus());
        assertEquals(5, report.getRequiredDatasetCount());
        assertEquals(5, report.getPresentRequiredDatasetCount());
        assertEquals(4, report.getValidRequiredDatasetCount());
        assertEquals(2, report.getIssueCount());
        assertTrue(report.getMissingDatasetCodes().isEmpty());
        assertEquals(1, report.getInvalidDatasetCodes().size());
        assertEquals("BASE_LAB_MFG_EXP", report.getInvalidDatasetCodes().get(0));
    }

    @Test
    void allRequiredTasksValidWithZeroIssuesAreReady() {
        for (String datasetCode : REQUIRED_DATASETS) {
            persistTask(datasetCode, ImportStatus.VALID, 0);
        }

        BatchReadiness batchReadiness = readiness.get(batchNo);
        DataQualityReport report = quality.get(batchNo);

        assertTrue(batchReadiness.isReadyForCalculation());
        assertEquals("READY", report.getStatus());
        assertEquals(8, report.getTotalDatasetCount());
        assertEquals(5, report.getRequiredDatasetCount());
        assertEquals(5, report.getPresentRequiredDatasetCount());
        assertEquals(5, report.getValidRequiredDatasetCount());
        assertEquals(0, report.getIssueCount());
        assertTrue(report.getMissingDatasetCodes().isEmpty());
        assertTrue(report.getInvalidDatasetCodes().isEmpty());
    }

    private void persistRequiredTasksExcept(String excludedDatasetCode) {
        for (String datasetCode : REQUIRED_DATASETS) {
            if (!datasetCode.equals(excludedDatasetCode)) {
                persistTask(datasetCode, ImportStatus.VALID, 0);
            }
        }
    }

    private void persistTask(String datasetCode, ImportStatus finalStatus, int issueCount) {
        String taskNo = "TASK-DQ-" + UUID.randomUUID().toString().replace("-", "");
        ImportTask task = tasks.save(new ImportTask(null, taskNo, batchNo, datasetCode,
                datasetCode + ".xlsx", "checksum-" + taskNo,
                "TEST-DATA-QUALITY-001-DB", LocalDateTime.now()));
        task.moveTo(ImportStatus.VALIDATING, 0);
        task = tasks.save(task);
        task.moveTo(finalStatus, issueCount);
        tasks.save(task);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " must be set for the real PostgreSQL contract test");
        }
        return value;
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
