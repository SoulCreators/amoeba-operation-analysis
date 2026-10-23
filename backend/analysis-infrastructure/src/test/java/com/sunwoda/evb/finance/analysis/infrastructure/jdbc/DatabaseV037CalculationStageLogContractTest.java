package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CALC-STAGE-V037：V037 阶段日志与心跳迁移、回滚和 verification 静态契约。
 *
 * <p>本次新增测试只验证数据库脚本引用的表、字段、约束与回滚范围，
 * 不测试或决定阶段推进业务策略、Worker 调度或权限。</p>
 */
class DatabaseV037CalculationStageLogContractTest {
    private static final String MIGRATION = "database/migration/V037__add_calculation_stage_log.sql";
    private static final String ROLLBACK = "database/rollback/V037__add_calculation_stage_log.sql";
    private static final String VERIFICATION = "database/verification/check_calculation_stage_log.sql";

    @Test
    void migrationAddsJobStageColumnsAndCreatesStageLogTableWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_stage_log"));
        assertTrue(executableSql.contains("ALTER TABLE flow.calculation_job"));
        for (String column : Arrays.asList(
                "ADD COLUMN IF NOT EXISTS stage VARCHAR(32)",
                "ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP",
                "ADD COLUMN IF NOT EXISTS error_code VARCHAR(64)",
                "ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128)",
                "ADD COLUMN IF NOT EXISTS processed_rows BIGINT NOT NULL DEFAULT 0",
                "ADD COLUMN IF NOT EXISTS issue_count INTEGER NOT NULL DEFAULT 0")) {
            assertTrue(executableSql.contains(column), "V037 must add " + column);
        }
        for (String stage : Arrays.asList("NORMALIZE", "MAP", "ALLOCATE", "PNL", "PVM", "RECONCILE", "MATERIALIZE")) {
            assertTrue(executableSql.contains("'" + stage + "'"), "V037 must whitelist stage " + stage);
        }
        for (String constraint : Arrays.asList(
                "ck_calculation_job_stage",
                "ck_calculation_job_processed_rows",
                "ck_calculation_job_issue_count",
                "uk_calculation_stage_log_job_stage",
                "fk_calculation_stage_log_job",
                "ck_calculation_stage_log_status",
                "ck_calculation_stage_log_failed_error")) {
            assertTrue(executableSql.contains(constraint), "V037 must define " + constraint);
        }
        assertTrue(executableSql.contains("NUMERIC(30, 6)"));
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V037 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV037ObjectsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.calculation_stage_log"));
        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.idx_calculation_stage_log_job_id"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS stage"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS heartbeat_at"));
        assertTrue(rollback.indexOf("DROP TABLE") < rollback.indexOf("DROP COLUMN IF EXISTS stage"),
                "V037 rollback must remove the stage log table before the job columns");
        assertFalse(rollback.contains("DROP SCHEMA"), "V037 rollback must not remove the shared flow schema");

        assertTrue(verification.contains("flow.calculation_stage_log"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"), "V037 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "uk_calculation_stage_log_job_stage",
                "fk_calculation_stage_log_job",
                "ck_calculation_stage_log_status",
                "ck_calculation_stage_log_failed_error")) {
            assertTrue(verification.contains(constraint), "V037 verification must reference " + constraint);
        }
        assertTrue(verification.contains("foreign_key_violation"),
                "V037 verification must probe the stage log foreign key");
        assertTrue(verification.contains("WHEN check_violation"),
                "V037 verification must probe database check constraints");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String stripSqlComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(MIGRATION))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root containing " + MIGRATION);
    }
}
