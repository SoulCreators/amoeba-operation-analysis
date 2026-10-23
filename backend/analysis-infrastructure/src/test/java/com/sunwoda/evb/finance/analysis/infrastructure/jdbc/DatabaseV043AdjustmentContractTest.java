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
 * NF-V043：V043 调整单表、约束与索引放行的迁移、回滚和 verification 静态契约。
 */
class DatabaseV043AdjustmentContractTest {
    private static final String MIGRATION = "database/migration/V043__create_adjustment.sql";
    private static final String ROLLBACK = "database/rollback/V043__create_adjustment.sql";
    private static final String VERIFICATION = "database/verification/check_adjustment.sql";

    @Test
    void migrationCreatesAdjustmentTableWithColumnsAndConstraintsWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS calc.adjustment"),
                "V043 must create calc.adjustment");
        for (String column : Arrays.asList(
                "adjustment_id VARCHAR(128)",
                "period VARCHAR(7)",
                "perspective VARCHAR(32)",
                "scope_code VARCHAR(128)",
                "scenario VARCHAR(16)",
                "metric_code VARCHAR(64)",
                "amount NUMERIC(24, 8)",
                "direction VARCHAR(16)",
                "reason VARCHAR(1000)",
                "evidence_file_id VARCHAR(128)",
                "expected_version VARCHAR(64)",
                "entered_by VARCHAR(128)",
                "status VARCHAR(16)",
                "entered_at TIMESTAMP",
                "voided_by VARCHAR(128)",
                "voided_at TIMESTAMP",
                "void_reason VARCHAR(1000)")) {
            assertTrue(executableSql.contains(column), "V043 must define column " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_adjustment_scenario",
                "ck_adjustment_direction",
                "ck_adjustment_status",
                "ck_adjustment_amount_non_negative",
                "ck_adjustment_period",
                "ck_adjustment_reason")) {
            assertTrue(executableSql.contains(constraint), "V043 must define constraint " + constraint);
        }
        assertTrue(executableSql.contains("idx_adjustment_period_status"),
                "V043 must define the period/status lookup index");
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V043 migration must not write business data");
    }

    @Test
    void rollbackDropsAdjustmentTableAndIndex() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));

        assertTrue(rollback.contains("DROP INDEX IF EXISTS calc.idx_adjustment_period_status"),
                "V043 rollback must drop the index");
        assertTrue(rollback.contains("DROP TABLE IF EXISTS calc.adjustment"),
                "V043 rollback must drop the table");
    }

    @Test
    void verificationProbesColumnsConstraintsAndRejectedStatesTransactionally() throws IOException {
        Path root = repositoryRoot();
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(verification.contains("calc.adjustment"));
        for (String constraint : Arrays.asList(
                "ck_adjustment_scenario",
                "ck_adjustment_direction",
                "ck_adjustment_status",
                "ck_adjustment_amount_non_negative")) {
            assertTrue(verification.contains(constraint),
                    "V043 verification must probe constraint " + constraint);
        }
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"),
                "V043 verification must not leave probe data committed");
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
