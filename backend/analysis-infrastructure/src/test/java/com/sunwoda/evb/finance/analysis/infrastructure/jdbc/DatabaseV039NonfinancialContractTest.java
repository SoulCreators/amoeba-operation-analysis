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
 * NF-V039：V039 非财经指标事实与结果表迁移、回滚和 verification 静态契约。
 *
 * <p>只验证脚本引用的表、字段、检查约束、表达式唯一索引与回滚范围，
 * 不测试或决定巴长映射来源、达成率口径或发布门禁。</p>
 */
class DatabaseV039NonfinancialContractTest {
    private static final String MIGRATION = "database/migration/V039__create_nonfinancial_metric_tables.sql";
    private static final String ROLLBACK = "database/rollback/V039__create_nonfinancial_metric_tables.sql";
    private static final String VERIFICATION = "database/verification/check_nonfinancial_metric.sql";

    @Test
    void migrationCreatesFactAndResultTablesWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.nonfinancial_fact"));
        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.nonfinancial_result"));
        for (String column : Arrays.asList(
                "period VARCHAR(7) NOT NULL",
                "base_code VARCHAR(64) NOT NULL",
                "production_line VARCHAR(64)",
                "cell_model VARCHAR(64) NOT NULL",
                "quality_category VARCHAR(64) NOT NULL",
                "target_actual VARCHAR(16) NOT NULL",
                "quality_value NUMERIC(30,10) NOT NULL",
                "pairing_status VARCHAR(16) NOT NULL",
                "leader_employee_no VARCHAR(64)",
                "leader_conflict BOOLEAN NOT NULL DEFAULT FALSE")) {
            assertTrue(executableSql.contains(column), "V039 must define " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_nonfinancial_fact_target_actual",
                "ck_nonfinancial_fact_source_row_no",
                "ck_nonfinancial_result_pairing_status",
                "uk_nonfinancial_fact_key",
                "uk_nonfinancial_result_key")) {
            assertTrue(executableSql.contains(constraint), "V039 must define " + constraint);
        }
        // 拉线可空，唯一键以 COALESCE 归一，空串与 NULL 视为同一键。
        assertTrue(executableSql.contains("COALESCE(production_line, '')"),
                "V039 unique indexes must normalize nullable production_line");
        assertFalse(executableSql.contains("ON DELETE CASCADE"),
                "V039 must not cascade delete nonfinancial lineage");
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V039 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV039ObjectsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.nonfinancial_result"));
        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.nonfinancial_fact"));
        assertFalse(rollback.contains("DROP SCHEMA"), "V039 rollback must not remove the shared flow schema");

        assertTrue(verification.contains("flow.nonfinancial_fact"));
        assertTrue(verification.contains("flow.nonfinancial_result"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"), "V039 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "ck_nonfinancial_fact_target_actual",
                "ck_nonfinancial_fact_source_row_no",
                "ck_nonfinancial_result_pairing_status",
                "uk_nonfinancial_fact_key",
                "uk_nonfinancial_result_key")) {
            assertTrue(verification.contains(constraint), "V039 verification must reference " + constraint);
        }
        assertTrue(verification.contains("unique_violation"),
                "V039 verification must probe the business-key unique indexes");
        assertTrue(verification.contains("check_violation"),
                "V039 verification must probe database check constraints");
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
