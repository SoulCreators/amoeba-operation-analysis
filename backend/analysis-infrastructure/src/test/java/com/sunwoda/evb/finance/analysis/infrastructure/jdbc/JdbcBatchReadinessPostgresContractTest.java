package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessServiceImpl;
import com.sunwoda.evb.finance.analysis.application.service.BatchServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryDatasetCatalog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executable PostgreSQL contract for DATA-001 readiness and JDBC rehydration. */
class JdbcBatchReadinessPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAnalysisBatchRepository batches;
    private static JdbcImportTaskRepository tasks;
    private static BatchReadinessService readiness;
    private static String batchNo;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; DATA-001 PostgreSQL readiness contract was not run");

        dataSource = new DriverManagerDataSource(
                url, environment("AMOEBA_DB_USERNAME"), environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT status FROM meta.analysis_batch LIMIT 0");
            jdbc.execute("SELECT status, issue_count FROM flow.import_task LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: DATA-001 PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
        batches = new JdbcAnalysisBatchRepository(dataSource);
        tasks = new JdbcImportTaskRepository(dataSource);
        readiness = new BatchReadinessServiceImpl(batches, tasks, new InMemoryDatasetCatalog());
        batchNo = "DATA001-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null || batchNo == null) return;
        jdbc.update("DELETE FROM flow.import_task WHERE batch_id IN "
                + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", batchNo);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
    }

    @Test
    void persistedValidTasksAreRehydratedAndGateReadyTransition() {
        AnalysisBatch batch = new AnalysisBatch(null, batchNo, "2026-01", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "data001-test", LocalDateTime.now());
        batch = batches.save(batch);
        batch.moveTo(BatchStatus.CHECKING);
        batches.save(batch);

        String[] required = {"BASE_ACT_INC_COST", "BASE_BUD_INC_COST", "BASE_IDLE",
                "BASE_L3_EXP", "BASE_LAB_MFG_EXP"};
        for (String dataset : required) {
            ImportTask task = tasks.save(new ImportTask(null, "TASK-" + dataset + "-" + batchNo,
                    batchNo, dataset, dataset + ".xlsx", "checksum-" + dataset,
                    "data001-test", LocalDateTime.now()));
            task.moveTo(ImportStatus.VALIDATING, 0);
            task = tasks.save(task);
            task.moveTo(ImportStatus.VALID, 0);
            tasks.save(task);
        }

        assertTrue(readiness.get(batchNo).isReadyForCalculation());
        assertEquals(ImportStatus.VALID, tasks.findByBatchNo(batchNo).get(0).getStatus());
        assertEquals(BatchStatus.READY_FOR_CALCULATION,
                new BatchServiceImpl(batches, readiness)
                        .move(batchNo, BatchStatus.READY_FOR_CALCULATION).getStatus());
    }

    @Test
    void missingRequiredTaskBlocksReadyTransition() {
        String missingBatchNo = batchNo + "-MISSING";
        AnalysisBatch batch = new AnalysisBatch(null, missingBatchNo, "2026-01", AnalysisPerspective.BASE,
                BatchStatus.CHECKING, "data001-test", LocalDateTime.now());
        batches.save(batch);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new BatchServiceImpl(batches,
                            new BatchReadinessServiceImpl(batches, tasks, new InMemoryDatasetCatalog()))
                            .move(missingBatchNo, BatchStatus.READY_FOR_CALCULATION));
        } finally {
            jdbc.update("DELETE FROM flow.import_task WHERE batch_id IN "
                    + "(SELECT id FROM meta.analysis_batch WHERE batch_no = ?)", missingBatchNo);
            jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no = ?", missingBatchNo);
        }
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
