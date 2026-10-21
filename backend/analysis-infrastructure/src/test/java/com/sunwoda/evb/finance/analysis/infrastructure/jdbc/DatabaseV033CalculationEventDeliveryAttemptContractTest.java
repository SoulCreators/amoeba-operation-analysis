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
 * V033 投递尝试历史迁移静态契约。
 *
 * <p>本次新增测试：锁定 BEGIN/END 追加式历史表的约束、追溯索引和回滚边界，防止迁移脚本覆盖业务数据。</p>
 */
class DatabaseV033CalculationEventDeliveryAttemptContractTest {
    private static final String MIGRATION = "database/migration/V033__create_calculation_event_delivery_attempt.sql";
    private static final String ROLLBACK = "database/rollback/V033__drop_calculation_event_delivery_attempt.sql";

    @Test
    void migrationDefinesAppendOnlyAttemptHistoryAndDoesNotWriteBusinessRows() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = migration.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--.*$", "");

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.calculation_event_delivery_attempt"));
        assertTrue(executableSql.contains("attempt_phase VARCHAR(16) NOT NULL"));
        assertTrue(executableSql.contains("attempt_phase = 'BEGIN'"));
        assertTrue(executableSql.contains("attempt_phase = 'END'"));
        assertTrue(executableSql.contains("idx_calculation_event_delivery_attempt_event"));
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|MERGE|TRUNCATE)\\b.*"),
                "V033 迁移不得写入既有业务数据");
    }

    @Test
    void rollbackOnlyDropsAttemptHistoryObjects() throws IOException {
        String rollback = read(repositoryRoot().resolve(ROLLBACK));

        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.idx_calculation_event_delivery_attempt_created"));
        assertTrue(rollback.contains("DROP INDEX IF EXISTS flow.idx_calculation_event_delivery_attempt_event"));
        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.calculation_event_delivery_attempt"));
        assertFalse(rollback.contains("DROP SCHEMA"));
        assertFalse(rollback.contains("flow.calculation_event_outbox;"));
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
