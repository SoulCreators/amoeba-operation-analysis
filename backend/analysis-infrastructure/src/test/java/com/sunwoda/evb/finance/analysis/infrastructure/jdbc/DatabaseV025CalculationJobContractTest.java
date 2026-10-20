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
 * CALC-001-JDBC-ADAPTER-001：V025 迁移、回滚和 verification 静态契约。
 *
 * <p>本次新增测试只验证数据库脚本引用的表、字段、约束、回滚范围和事务探针，
 * 不测试或决定计算 Service、Controller、Worker、重试、历史版本、批次结果事务和权限。</p>
 */
class DatabaseV025CalculationJobContractTest {
    private static final String MIGRATION = "database/migration/V025__create_calculation_job.sql";
    private static final String ROLLBACK = "database/rollback/V025__create_calculation_job.sql";
    private static final String VERIFICATION = "database/verification/check_calculation_job.sql";

    @Test
    void migrationCreatesCalculationJobStructureWithoutBusinessDataWrites() throws IOException {
        // 本次新增契约：V025 只建立 JDBC 适配器所需结构，不在迁移中写入业务数据。
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_job"));
        for (String column : Arrays.asList(
                "job_id VARCHAR(128) NOT NULL",
                "request_key VARCHAR(255) NOT NULL",
                "batch_no VARCHAR(128) NOT NULL",
                "status VARCHAR(32) NOT NULL",
                "attempt_no INTEGER NOT NULL",
                "last_error TEXT",
                "created_at TIMESTAMP NOT NULL",
                "started_at TIMESTAMP",
                "finished_at TIMESTAMP")) {
            assertTrue(executableSql.contains(column), "V025 must declare " + column);
        }
        for (String constraint : Arrays.asList(
                "uk_calculation_job_job_id",
                "ck_calculation_job_status",
                "ck_calculation_job_attempt_no",
                "ck_calculation_job_started_at",
                "ck_calculation_job_finished_at",
                "ck_calculation_job_failed_error",
                "uk_calculation_job_active_request_key")) {
            assertTrue(executableSql.contains(constraint), "V025 must define " + constraint);
        }
        assertTrue(executableSql.contains("status IN ('QUEUED', 'RUNNING')"));
        assertTrue(executableSql.contains("status <> 'FAILED' OR"));
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V025 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV025ObjectsAndConstraintsTransactionally() throws IOException {
        // 本次新增契约：回滚只移除 V025 对象，verification 覆盖表、约束和事务性探针。
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.uk_calculation_job_active_request_key"));
        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.calculation_job"));
        assertFalse(rollback.contains("DROP SCHEMA"),
                "V025 rollback must not remove the shared flow schema");
        assertTrue(rollback.indexOf("DROP INDEX") < rollback.indexOf("DROP TABLE"),
                "V025 rollback must remove the dependent index before the table");

        assertTrue(verification.contains("flow.calculation_job"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"),
                "V025 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "uk_calculation_job_job_id",
                "ck_calculation_job_status",
                "ck_calculation_job_attempt_no",
                "ck_calculation_job_started_at",
                "ck_calculation_job_finished_at",
                "ck_calculation_job_failed_error",
                "uk_calculation_job_active_request_key")) {
            assertTrue(verification.contains(constraint),
                    "V025 verification must reference " + constraint);
        }
        assertTrue(verification.contains("WHEN unique_violation"),
                "V025 verification must probe active requestKey and duplicate jobId rejection");
        assertTrue(verification.contains("WHEN check_violation"),
                "V025 verification must probe database check constraints");
        assertTrue(verification.contains("'UNKNOWN'"));
        assertTrue(verification.contains("attempt_no"));
        assertTrue(verification.contains("'FAILED'"));
        assertTrue(verification.contains("last_error"));
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
