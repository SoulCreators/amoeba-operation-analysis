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
 * V034 重试租约 token 迁移静态契约。
 *
 * <p>本次新增测试：锁定 token 补齐、非空约束、唯一索引和仅回滚 token 对象的边界。</p>
 */
class DatabaseV034CalculationRetryClaimTokenContractTest {
    private static final String MIGRATION = "database/migration/V034__add_calculation_retry_claim_token.sql";
    private static final String ROLLBACK = "database/rollback/V034__drop_calculation_retry_claim_token.sql";

    @Test
    void migrationBackfillsAndConstrainsClaimTokenWithoutChangingBusinessRows() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = migration.replaceAll("(?m)--.*$", "");
        assertTrue(executableSql.contains("ADD COLUMN IF NOT EXISTS claim_token"));
        assertTrue(executableSql.contains("SET claim_token"));
        assertTrue(executableSql.contains("ALTER COLUMN claim_token SET NOT NULL"));
        assertTrue(executableSql.contains("ck_calculation_retry_claim_token"));
        assertTrue(executableSql.contains("uk_calculation_retry_claim_job_token"));
        assertFalse(executableSql.matches("(?is).*\\b(DELETE|TRUNCATE|DROP TABLE|DROP SCHEMA)\\b.*"));
    }

    @Test
    void rollbackOnlyRemovesTokenObjects() throws IOException {
        String rollback = read(repositoryRoot().resolve(ROLLBACK));
        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.uk_calculation_retry_claim_job_token"));
        assertTrue(rollback.contains("DROP CONSTRAINT IF EXISTS ck_calculation_retry_claim_token"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS claim_token"));
        assertFalse(rollback.contains("DROP TABLE"));
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
