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
 * V032 迁移与回滚静态契约。
 *
 * <p>本次新增测试：锁定领取协调表、唯一令牌、租约索引和回滚边界，防止迁移脚本写入既有业务数据。</p>
 */
class DatabaseV032CalculationEventOutboxClaimContractTest {
    private static final String MIGRATION = "database/migration/V032__create_calculation_event_outbox_claim.sql";
    private static final String ROLLBACK = "database/rollback/V032__drop_calculation_event_outbox_claim.sql";

    @Test
    void migrationDefinesIndependentClaimTableAndDoesNotWriteBusinessRows() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = migration.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_event_outbox_claim"));
        assertTrue(executableSql.contains("claim_token VARCHAR(128) NOT NULL"));
        assertTrue(executableSql.contains("claimed_until TIMESTAMP NOT NULL"));
        assertTrue(executableSql.contains("uk_calculation_event_outbox_claim_token"));
        assertTrue(executableSql.contains("idx_calculation_event_outbox_claim_expiry"));
        // 外键的 ON DELETE CASCADE 是约束语义，不属于迁移阶段删除业务数据。
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|MERGE|TRUNCATE)\\b.*"),
                "V032 迁移不得写入既有业务数据");
    }

    @Test
    void rollbackOnlyDropsClaimIndexAndTable() throws IOException {
        String rollback = read(repositoryRoot().resolve(ROLLBACK));

        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.idx_calculation_event_outbox_claim_expiry"));
        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.calculation_event_outbox_claim"));
        assertFalse(rollback.contains("DROP SCHEMA"));
        assertFalse(rollback.contains("DROP TABLE IF EXISTS flow.calculation_event_outbox;"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
