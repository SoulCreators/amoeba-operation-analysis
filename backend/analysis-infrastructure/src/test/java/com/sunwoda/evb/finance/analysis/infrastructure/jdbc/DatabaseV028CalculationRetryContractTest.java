package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V028 迁移、回滚和 verification 静态契约。
 *
 * <p>本次新增测试：锁定脚本边界，确保 V028 独立落表并保留幂等/人工审计探针。</p>
 */
class DatabaseV028CalculationRetryContractTest {
    private static final String MIGRATION = "database/migration/V028__create_calculation_retry.sql";
    private static final String ROLLBACK = "database/rollback/V028__create_calculation_retry.sql";
    private static final String VERIFICATION = "database/verification/check_calculation_retry.sql";

    @Test
    void migrationDefinesRetryAndActionTablesWithoutBusinessWrites() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = migration.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_retry"));
        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_retry_action"));
        for (String expected : new String[]{
                "uk_calculation_retry_job_id", "uk_calculation_retry_request_key",
                "ck_calculation_retry_status", "ck_calculation_retry_schedule",
                "ck_calculation_retry_failure_detail", "ck_calculation_retry_action_type",
                "original_attempt_no", "operator", "reason", "result"}) {
            assertTrue(executableSql.contains(expected), "V028 must define " + expected);
        }
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V028 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationAreScopedAndTransactional() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.indexOf("calculation_retry_action") < rollback.indexOf("calculation_retry;"));
        assertFalse(rollback.contains("DROP SCHEMA"));
        assertTrue(verification.contains("uk_calculation_retry_request_key"));
        assertTrue(verification.contains("WHEN unique_violation"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve(MIGRATION))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root containing " + MIGRATION);
    }
}
