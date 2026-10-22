package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.service.*;
import com.sunwoda.evb.finance.analysis.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrdinaryCalculationPostgresContractTest {
    static AnnotationConfigApplicationContext context;
    static DataSource dataSource;
    static JdbcTemplate jdbc;
    static CalculationService service;
    static JdbcCalculationRunStore store;
    String batch, task;
    long batchId;

    @BeforeAll static void start() {
        assumeTrue(System.getenv("AMOEBA_DB_URL") != null, "PostgreSQL environment required");
        dataSource = new DriverManagerDataSource(System.getenv("AMOEBA_DB_URL"),
                System.getenv("AMOEBA_DB_USERNAME"), System.getenv("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("db");
        context.register(Config.class);
        context.refresh();
        service = context.getBean(CalculationService.class);
        store = context.getBean(JdbcCalculationRunStore.class);
    }
    @AfterAll static void stop() { if (context != null) context.close(); }
    @BeforeEach void seed() {
        String id = UUID.randomUUID().toString();
        batch = "ORD-" + id; task = "TASK-" + id;
        batchId = jdbc.queryForObject("INSERT INTO meta.analysis_batch(batch_no, period, status, perspective, created_by) "
                + "VALUES (?, '2099-12', 'READY_FOR_CALCULATION', 'BASE', 'test') RETURNING id", Long.class, batch);
        jdbc.update("INSERT INTO flow.import_task(task_no, batch_id, dataset_code, file_name, object_key, checksum, status, created_by) "
                + "VALUES (?, ?, 'TEST', 'source.xlsx', 'test/source.xlsx', 'sha-original', 'VALID', 'test')", task, batchId);
        jdbc.update("INSERT INTO raw.pnl_fact(batch_id, perspective, source_task_no, period, scope_code, volume, revenue, sales_cost, "
                + "budget_volume, budget_revenue, budget_sales_cost, material_cost, budget_material_cost) "
                + "VALUES (?, 'BASE', ?, '2099-12', 'A', 10, 100, 60, 8, 80, 40, 60, 40)", batchId, task);
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, can_view_detail) "
                + "VALUES (?, 'HQ_FINANCE', 'BASE', 'A', TRUE)", batch);
    }
    @AfterEach void clean() {
        jdbc.update("DELETE FROM flow.calculation_event_outbox WHERE calculation_version_id IN (SELECT calculation_version_id FROM calc.calculation_version WHERE batch_no = ?)", batch);
        jdbc.update("DELETE FROM calc.calculation_reconciliation WHERE calculation_version_id IN (SELECT calculation_version_id FROM calc.calculation_version WHERE batch_no = ?)", batch);
        jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN (SELECT id FROM calc.pnl_result WHERE batch_id = ?)", batchId);
        jdbc.update("DELETE FROM calc.pnl_result WHERE batch_id = ?", batchId);
        List<String> snapshots = jdbc.query("SELECT snapshot_id FROM calc.calculation_version WHERE batch_no = ?", (rs,n)->rs.getString(1), batch);
        jdbc.update("DELETE FROM calc.calculation_version WHERE batch_no = ?", batch);
        for (String id : snapshots) jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?", id);
        jdbc.update("DELETE FROM flow.calculation_job WHERE batch_no = ?", batch);
        jdbc.update("DELETE FROM raw.pnl_fact WHERE batch_id = ?", batchId);
        jdbc.update("DELETE FROM flow.import_task WHERE batch_id = ?", batchId);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE id = ?", batchId);
        jdbc.update("DELETE FROM iam.user_scope WHERE user_id = ?", batch);
    }

    @Test void recalculateAppendsAndHistoryReadsFrozenFactsWithScopeAuthorization() {
        CalculationResult first = service.calculate(batch, batch);
        String originalSnapshot = snapshotContent(first.getVersionNo());
        jdbc.update("UPDATE raw.pnl_fact SET revenue = 123.123456789 WHERE batch_id = ?", batchId);
        jdbc.update("UPDATE flow.import_task SET checksum = 'sha-new' WHERE task_no = ?", task);
        CalculationResult second = service.calculate(batch, batch);
        assertEquals("R1", first.getVersionNo()); assertEquals("R2", second.getVersionNo());
        assertEquals(2, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ?", batch));
        assertEquals(1, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ? AND current_flag", batch));
        assertEquals(originalSnapshot, snapshotContent("R1"));
        assertNotEquals(originalSnapshot, snapshotContent("R2"));
        CalculationResult historical = service.getVersionForUser(batch, "R1", batch);
        assertDecimal("100", historical.getResults().get(0).getLines().get("REVENUE"));
        assertDecimal("100", historical.getFacts().get(0).getRevenue());
        assertEquals(0, count("SELECT count(*) FROM calc.pnl_result WHERE batch_id = ? AND calculation_version_id IS NULL", batchId));
        assertThrows(IllegalStateException.class, () -> service.getVersionForUser(batch, "R1", "unauthorized"));
    }

    @Test void failureAfterResultSaveRollsBackVersionPointerSnapshotAndOutbox() {
        service.calculate(batch, batch);
        String versionId = currentId();
        CalculationResult original = service.get(batch);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> store.execute(batch, batch, version -> {
            jdbc.update("UPDATE meta.analysis_batch SET status = 'CALCULATING' WHERE id = ?", batchId);
            context.getBean(JdbcCalculationResultRepository.class).save(new CalculationResult(batch, AnalysisPerspective.BASE,
                    original.getResults(), version, CalculationResultStatus.CALCULATED, null, null,
                    java.time.LocalDateTime.now(), null, null, original.getFacts()));
            throw new IllegalStateException("injected failure after materialization");
        }));
        assertEquals("injected failure after materialization", failure.getMessage());
        assertEquals(versionId, currentId());
        assertEquals(1, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ?", batch));
        assertEquals("CALCULATED", jdbc.queryForObject("SELECT status FROM meta.analysis_batch WHERE id = ?", String.class, batchId));
        assertEquals(1, count("SELECT count(*) FROM flow.calculation_job WHERE batch_no = ?", batch));
    }

    @Test void sourceEvidenceAndAuthorizationFailWithoutLeavingCandidate() {
        jdbc.update("UPDATE flow.import_task SET checksum = NULL WHERE task_no = ?", task);
        assertThrows(IllegalStateException.class, () -> service.calculate(batch, batch));
        assertEquals(0, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ?", batch));
        jdbc.update("UPDATE flow.import_task SET checksum = 'sha' WHERE task_no = ?", task);
        assertThrows(IllegalStateException.class, () -> service.calculate(batch, "denied"));
        assertEquals(0, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ?", batch));
        assertEquals("READY_FOR_CALCULATION", jdbc.queryForObject("SELECT status FROM meta.analysis_batch WHERE id = ?", String.class, batchId));
    }

    @Test void zeroVolumeAndFormalGrainRemainBlockedAndConfirmationCannotRewriteAmounts() {
        jdbc.update("UPDATE raw.pnl_fact SET volume = 0, revenue = 0 WHERE batch_id = ?", batchId);
        service.calculate(batch, batch);
        String id = currentId();
        assertEquals(1, count("SELECT count(*) FROM calc.calculation_reconciliation WHERE calculation_version_id = ? AND check_code = 'PVM_ZERO_VOLUME' AND status = 'BLOCKED'", id));
        assertThrows(IllegalStateException.class, () -> service.confirm(batch, batch));
        // Exercise the repository lifecycle contract separately from the blocked application gate.
        context.getBean(JdbcCalculationResultRepository.class).save(service.get(batch).confirm(batch));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM calc.calculation_version WHERE calculation_version_id = ?", String.class, id));
        assertThrows(IllegalStateException.class, () -> service.publish(batch, batch));
        assertEquals("CALCULATED", jdbc.queryForObject("SELECT status FROM meta.analysis_batch WHERE id = ?", String.class, batchId));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE calc.calculation_snapshot SET content_hash = 'tampered' WHERE snapshot_id = (SELECT snapshot_id FROM calc.calculation_version WHERE calculation_version_id = ?)", id));
        assertThrows(IllegalStateException.class, () -> context.getBean(JdbcCalculationResultRepository.class).save(service.get(batch)));
    }

    @Test void competingDatabaseSessionCannotStartSameBatch() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch acquired = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(tx -> {
            store.lockBatch(batch); acquired.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException ex) { throw new RuntimeException(ex); }
            return null;
        }));
        try {
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            assertThrows(RuntimeException.class, () -> service.calculate(batch, batch));
            assertEquals(0, count("SELECT count(*) FROM calc.calculation_version WHERE batch_no = ?", batch));
        } finally { release.countDown(); holder.get(10, TimeUnit.SECONDS); pool.shutdownNow(); }
    }

    @Test void samePeriodDatasetIsExclusiveEvenWithoutHoldingTheBatchRow() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch acquired = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(tx -> {
            assertTrue(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('ordinary-calculation'), hashtext('2099-12:TEST'))", Boolean.class));
            acquired.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException ex) { throw new RuntimeException(ex); }
            return null;
        }));
        try {
            assertTrue(acquired.await(10, TimeUnit.SECONDS));
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.calculate(batch, batch));
            assertTrue(error.getMessage().contains("同期间数据集"));
        } finally { release.countDown(); holder.get(10, TimeUnit.SECONDS); pool.shutdownNow(); }
    }

    @Test void repeatingDecimalDifferenceIsRetainedAtZeroTolerance() {
        jdbc.update("UPDATE raw.pnl_fact SET volume = 3, revenue = 100, budget_volume = 3, budget_revenue = 90 WHERE batch_id = ?", batchId);
        service.calculate(batch, batch);
        BigDecimal difference = jdbc.queryForObject("SELECT difference FROM calc.calculation_reconciliation "
                + "WHERE calculation_version_id = ? AND check_code = 'PVM_REVENUE'", BigDecimal.class, currentId());
        assertNotEquals(0, difference.signum());
        assertTrue(difference.abs().compareTo(new BigDecimal("0.000001")) < 0);
        assertEquals("FAIL", jdbc.queryForObject("SELECT status FROM calc.calculation_reconciliation WHERE calculation_version_id = ? AND check_code = 'PVM_REVENUE'", String.class, currentId()));
    }

    @Test void snapshotIdCollisionRejectsDifferentContentAndDatabaseRejectsProvenanceRewrite() {
        service.calculate(batch, batch);
        JdbcCalculationSnapshotRepository repository = new JdbcCalculationSnapshotRepository(dataSource);
        String snapshotId = jdbc.queryForObject("SELECT snapshot_id FROM calc.calculation_version WHERE calculation_version_id = ?", String.class, currentId());
        CalculationSnapshot original = repository.findById(snapshotId).get();
        repository.save(original);
        CalculationSnapshot tampered = new CalculationSnapshot(snapshotId, original.getSnapshotType(), original.getSchemaVersion(),
                "{}", original.getSourceVersionSetJson(), original.getContentHash(), original.getSourceVersionHash(),
                original.getRuleVersion(), original.getCreatedBy(), original.getCreatedAt());
        assertThrows(IllegalStateException.class, () -> repository.save(tampered));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE calc.calculation_version SET config_hash = 'tampered' WHERE calculation_version_id = ?", currentId()));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE calc.calculation_version SET status = 'PUBLISHED' WHERE calculation_version_id = ?", currentId()));
    }

    String currentId() { return jdbc.queryForObject("SELECT calculation_version_id FROM calc.calculation_version WHERE batch_no = ? AND current_flag", String.class, batch); }
    String snapshotContent(String version) { return jdbc.queryForObject("SELECT s.content_json::text FROM calc.calculation_snapshot s JOIN calc.calculation_version v ON v.snapshot_id = s.snapshot_id WHERE v.batch_no = ? AND v.version_no = ?", String.class, batch, version); }
    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    void assertDecimal(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { return dataSource; }
        @Bean PlatformTransactionManager transactionManager() { return new DataSourceTransactionManager(dataSource); }
        @Bean JdbcAnalysisBatchRepository batches() { return new JdbcAnalysisBatchRepository(dataSource); }
        @Bean JdbcCalculationResultRepository results() { return new JdbcCalculationResultRepository(dataSource); }
        @Bean CalculationVersionMaterializationService materializer() { return new CalculationVersionMaterializationService(new JdbcCalculationSnapshotRepository(dataSource), new JdbcCalculationVersionRepository(dataSource)); }
        @Bean JdbcCalculationRunStore store() { return new JdbcCalculationRunStore(dataSource, transactionManager(), materializer()); }
        @Bean CalculationServiceImpl service() {
            PermissionService permissions = mock(PermissionService.class);
            when(permissions.canCalculate(anyString(), any(), anySet())).thenAnswer(a -> !"denied".equals(a.getArgument(0)));
            when(permissions.canConfirm(anyString(), any(), anySet())).thenReturn(true);
            when(permissions.canPublish(anyString(), any(), anySet())).thenReturn(true);
            when(permissions.canViewHistory(anyString(), any())).thenReturn(true);
            when(permissions.canViewResult(anyString(), any(), any())).thenReturn(true);
            when(permissions.viewableScopes(anyString(), any())).thenReturn(Collections.singleton("A"));
            when(permissions.detailScopes(anyString(), any())).thenReturn(Collections.singleton("A"));
            CalculationServiceImpl service = new CalculationServiceImpl(batches(), new JdbcPnlFactProvider(dataSource), permissions, results());
            service.setRunStore(store());
            return service;
        }
    }
}
