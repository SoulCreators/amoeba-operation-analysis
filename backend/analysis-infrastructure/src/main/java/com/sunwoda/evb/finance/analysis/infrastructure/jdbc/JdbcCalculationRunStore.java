package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRunStore;
import com.sunwoda.evb.finance.analysis.application.model.CalculationVersionMaterializationCommand;
import com.sunwoda.evb.finance.analysis.application.service.CalculationChecks;
import com.sunwoda.evb.finance.analysis.application.service.CalculationVersionMaterializationService;
import com.sunwoda.evb.finance.analysis.domain.model.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Production boundary for the existing ordinary calculation engine. */
@Repository
@Profile("db")
public class JdbcCalculationRunStore implements CalculationRunStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final CalculationVersionMaterializationService materializer;

    public JdbcCalculationRunStore(DataSource dataSource, PlatformTransactionManager manager,
                                   CalculationVersionMaterializationService materializer) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.materializer = materializer;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public CalculationResult execute(String batchNo, String operator, Function<String, CalculationResult> calculation) {
        return transaction.execute(tx -> {
            lockBatch(batchNo);
            Map<String, Object> batch = jdbc.queryForMap(
                    "SELECT id, perspective, status, period FROM meta.analysis_batch WHERE batch_no = ?", batchNo);
            String status = (String) batch.get("status");
            if (!"READY_FOR_CALCULATION".equals(status) && !"CALCULATED".equals(status)) {
                throw new IllegalStateException("批次尚未完成校验或已锁定: " + status);
            }
            long batchId = ((Number) batch.get("id")).longValue();
            String perspective = (String) batch.get("perspective");
            List<String> datasets = jdbc.query("SELECT DISTINCT t.dataset_code FROM flow.import_task t "
                    + "JOIN raw.pnl_fact f ON f.source_task_no = t.task_no AND f.batch_id = t.batch_id "
                    + "WHERE f.batch_id = ? AND f.perspective = ? ORDER BY t.dataset_code",
                    (rs, n) -> rs.getString(1), batchId, perspective);
            for (String dataset : datasets) {
                Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('ordinary-calculation'), hashtext(?))",
                        Boolean.class, batch.get("period") + ":" + dataset);
                if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("同期间数据集已有计算任务: " + dataset);
            }
            assertSourceEvidence(batchId, perspective);
            String sources = jsonArray("SELECT DISTINCT t.* FROM flow.import_task t JOIN raw.pnl_fact f "
                    + "ON f.source_task_no = t.task_no AND f.batch_id = t.batch_id "
                    + "WHERE f.batch_id = ? AND f.perspective = ?", batchId, perspective);
            String facts = jsonArray("SELECT * FROM raw.pnl_fact WHERE batch_id = ? AND perspective = ?", batchId, perspective);
            String traces = jsonArray("SELECT * FROM raw.pnl_fact_trace WHERE batch_id = ? AND perspective = ?", batchId, perspective);
            String config = "{\"mappingEntries\":" + jsonArray("SELECT * FROM cfg.mapping_entry")
                    + ",\"metricDefinitions\":" + jsonArray("SELECT * FROM cfg.metric_definition") + "}";
            String content = "{\"facts\":" + facts + ",\"sourceRows\":" + traces
                    + ",\"configurationContext\":" + config
                    + ",\"configurationUsage\":\"UPSTREAM_CONTEXT_ONLY\",\"ruleVersion\":\""
                    + CalculationChecks.RULE_VERSION + "\"}";
            String sourceHash = sha256(sources), configHash = sha256(config);
            String fingerprint = sha256(batchNo + "\n" + sourceHash + "\n" + sha256(content));
            String id = UUID.randomUUID().toString();
            String jobId = "JOB-" + id, snapshotId = "SNAP-" + id, versionId = "CALC-" + id;
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            // All versions, including manual revisions, reserve a sequence position.
            long ordinal = jdbc.queryForObject("SELECT count(*) + 1 FROM calc.calculation_version WHERE batch_id = ?",
                    Long.class, batchId);
            String versionNo = "R" + ordinal;
            List<String> previous = jdbc.query("SELECT calculation_version_id FROM calc.calculation_version "
                            + "WHERE batch_id = ? AND current_flag = TRUE", (rs, n) -> rs.getString(1), batchId);
            jdbc.update("INSERT INTO flow.calculation_job(job_id, request_key, batch_no, status, created_at, started_at) "
                    + "VALUES (?, ?, ?, 'RUNNING', ?, ?)", jobId, "ORDINARY:" + batchNo, batchNo, now, now);
            jdbc.update("UPDATE calc.calculation_version SET current_flag = FALSE WHERE batch_id = ? AND current_flag = TRUE", batchId);
            materializer.materialize(new CalculationVersionMaterializationCommand(
                    new CalculationSnapshot(snapshotId, "ORDINARY_INPUT", "ORDINARY-1", content, sources,
                            sha256(content), sourceHash, CalculationChecks.RULE_VERSION, operator, now),
                    new CalculationVersion(versionId, batchNo, AnalysisPerspective.fromCode(perspective), versionNo,
                            jobId, snapshotId, sourceHash, configHash, fingerprint, CalculationVersionStatus.CALCULATED,
                            true, previous.isEmpty() ? null : previous.get(0), "TRACE-" + id, operator, now,
                            null, null, null, null)));
            CalculationResult result = calculation.apply(versionNo);
            for (CalculationChecks.Check check : CalculationChecks.evaluate(result)) {
                saveCheck(versionId, check.scopeCode, check.code, check.expected, check.actual,
                        check.difference, check.status, check.reason, sources);
            }
            // Compare every directly aggregated field against the frozen database view, before any rounding.
            for (PnlResult pnl : result.getResults()) {
                for (String field : new String[]{"VOLUME", "REVENUE", "SALES_COST", "IDLE_EXPENSE", "BASE_EXPENSE",
                        "RD_EXPENSE", "ASSET_IMPAIRMENT", "CREDIT_IMPAIRMENT", "OTHER_INCOME", "MATERIAL_COST",
                        "LABOR_COST", "OUTSOURCING_COST", "VARIABLE_MFG_COST", "FIXED_MFG_COST"}) {
                    for (String prefix : new String[]{"", "BUDGET_"}) {
                        String code = prefix + field;
                        BigDecimal expected = jdbc.queryForObject("SELECT COALESCE(sum(" + code.toLowerCase(java.util.Locale.ROOT)
                                + "), 0) FROM raw.pnl_fact WHERE batch_id = ? AND perspective = ? AND scope_code = ?",
                                BigDecimal.class, batchId, perspective, pnl.getScopeCode());
                        BigDecimal actual = resultLine(pnl, code), diff = actual.subtract(expected);
                        saveCheck(versionId, pnl.getScopeCode(), "SOURCE_" + code, expected, actual, diff,
                                diff.signum() == 0 ? "PASS" : "FAIL", null, sources);
                    }
                }
            }
            jdbc.update("UPDATE flow.calculation_job SET status = 'SUCCEEDED', finished_at = ? WHERE job_id = ?",
                    LocalDateTime.now(ZoneId.of("Asia/Shanghai")), jobId);
            jdbc.update("INSERT INTO flow.calculation_event_outbox(event_id, aggregate_type, aggregate_id, event_type, "
                    + "calculation_version_id, trace_id, payload_json) VALUES (?, 'CALCULATION_VERSION', ?, 'CALCULATED', ?, ?, "
                    + "jsonb_build_object('versionId', CAST(? AS text), 'ruleVersion', CAST(? AS text)))",
                    "EVENT-" + id, versionId, versionId, "TRACE-" + id, versionId, CalculationChecks.RULE_VERSION);
            return result;
        });
    }

    @Override
    public void lockBatch(String batchNo) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("计算与生命周期操作必须在数据库事务内执行");
        }
        List<Long> ids = jdbc.query("SELECT id FROM meta.analysis_batch WHERE batch_no = ? FOR UPDATE NOWAIT",
                (rs, n) -> rs.getLong(1), batchNo);
        if (ids.isEmpty()) throw new IllegalArgumentException("批次不存在: " + batchNo);
    }

    @Override
    public void assertPublishable(CalculationResult result) {
        List<String> ids = jdbc.query("SELECT v.calculation_version_id FROM calc.calculation_version v "
                        + "JOIN calc.calculation_snapshot s ON s.snapshot_id = v.snapshot_id "
                        + "WHERE v.batch_no = ? AND v.version_no = ? AND v.current_flag = TRUE AND s.schema_version = 'ORDINARY-1'",
                (rs, n) -> rs.getString(1), result.getBatchNo(), result.getVersionNo());
        if (ids.size() != 1) throw new IllegalStateException("缺少普通计算的完整版本和输入快照，禁止发布");
        List<String> failures = jdbc.query("SELECT check_code || ':' || status FROM calc.calculation_reconciliation "
                + "WHERE calculation_version_id = ? AND status <> 'PASS' ORDER BY scope_code, check_code",
                (rs, n) -> rs.getString(1), ids.get(0));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM calc.calculation_reconciliation WHERE calculation_version_id = ?",
                Integer.class, ids.get(0));
        if (count == 0 || !failures.isEmpty()) throw new IllegalStateException("零容差发布门禁未通过: " + failures);
    }

    private void assertSourceEvidence(long batchId, String perspective) {
        Integer invalid = jdbc.queryForObject("SELECT count(*) FROM raw.pnl_fact f LEFT JOIN flow.import_task t "
                + "ON t.task_no = f.source_task_no AND t.batch_id = f.batch_id WHERE f.batch_id = ? AND f.perspective = ? "
                + "AND (t.id IS NULL OR t.status <> 'VALID' OR NULLIF(btrim(t.checksum), '') IS NULL "
                + "OR NULLIF(btrim(t.object_key), '') IS NULL)", Integer.class, batchId, perspective);
        if (invalid > 0) throw new IllegalStateException("源文件集合缺少有效任务、文件哈希或存储定位，不能生成可信计算版本");
    }
    private String jsonArray(String query, Object... args) {
        return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(x) ORDER BY x.id), '[]'::jsonb)::text FROM ("
                + query + ") x", String.class, args);
    }
    private void saveCheck(String version, String scope, String code, BigDecimal expected, BigDecimal actual,
                           BigDecimal diff, String status, String reason, String sources) {
        jdbc.update("INSERT INTO calc.calculation_reconciliation(calculation_version_id, scope_code, check_code, "
                + "expected, actual, difference, status, reason, source_ids) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                version, scope, code, expected, actual, diff, status, reason, sources);
    }
    private BigDecimal resultLine(PnlResult result, String code) {
        BigDecimal value = result.getLines().get(code);
        if (value == null) throw new IllegalStateException("结果缺少控制项: " + code);
        return value;
    }
    static String sha256(String content) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b & 0xff));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
