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
 * NF-V042：V042 结果撤回列与状态约束放行的迁移、回滚和 verification 静态契约。
 */
class DatabaseV042ResultWithdrawalContractTest {
    private static final String MIGRATION = "database/migration/V042__add_result_withdrawal.sql";
    private static final String ROLLBACK = "database/rollback/V042__add_result_withdrawal.sql";
    private static final String VERIFICATION = "database/verification/check_result_withdrawal.sql";

    @Test
    void migrationAddsWithdrawalColumnsAndRelaxesStatusConstraintsWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        for (String column : Arrays.asList(
                "withdrawn_by VARCHAR(64)",
                "withdrawn_at TIMESTAMP",
                "withdraw_reason VARCHAR(1000)",
                "notification_targets VARCHAR(1000)")) {
            assertTrue(executableSql.contains(column), "V042 must add " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_export_task_snapshot_status",
                "ck_calculation_version_status")) {
            assertTrue(executableSql.contains(constraint), "V042 must relax " + constraint);
        }
        assertTrue(executableSql.contains("'WITHDRAWN'"),
                "V042 must admit WITHDRAWN into the relaxed status constraints");
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V042 migration must not write business data");
    }

    @Test
    void rollbackRestoresOriginalConstraintsAndRemovesWithdrawalColumns() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));

        for (String column : Arrays.asList(
                "withdrawn_by", "withdrawn_at", "withdraw_reason", "notification_targets")) {
            assertTrue(rollback.contains("DROP COLUMN IF EXISTS " + column),
                    "V042 rollback must drop " + column);
        }
        // 回滚须恢复不含 WITHDRAWN 的原始状态集合。
        assertTrue(rollback.contains("result_status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED')"),
                "V042 rollback must restore the export_task snapshot status constraint");
        assertTrue(rollback.contains("status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED', 'SUPERSEDED')"),
                "V042 rollback must restore the calculation_version status constraint");
    }

    @Test
    void verificationProbesWithdrawalColumnsAndRelaxedConstraintsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(verification.contains("calc.pnl_result"));
        assertTrue(verification.contains("withdrawn_by"));
        assertTrue(verification.contains("ck_export_task_snapshot_status"));
        assertTrue(verification.contains("ck_calculation_version_status"));
        assertTrue(verification.contains("WITHDRAWN"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"),
                "V042 verification must not leave probe data committed");
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
