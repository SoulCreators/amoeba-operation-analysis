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
 * NF-V044：V044 计算版本追踪号索引的迁移、回滚和 verification 静态契约。
 */
class DatabaseV044CalculationVersionTraceIndexContractTest {
    private static final String MIGRATION = "database/migration/V044__add_calculation_version_trace_index.sql";
    private static final String ROLLBACK = "database/rollback/V044__add_calculation_version_trace_index.sql";
    private static final String VERIFICATION = "database/verification/check_calculation_version_trace_index.sql";

    @Test
    void migrationAddsTraceIndexWithoutBusinessData() throws IOException {
        String migration = stripSqlComments(read(repositoryRoot().resolve(MIGRATION)));

        assertTrue(migration.contains("idx_calculation_version_trace"),
                "V044 must define idx_calculation_version_trace");
        assertTrue(migration.contains("ON calc.calculation_version(trace_id)"),
                "V044 must index calc.calculation_version.trace_id");
        assertFalse(migration.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V044 migration must not write business data");
    }

    @Test
    void rollbackDropsTraceIndex() throws IOException {
        String rollback = read(repositoryRoot().resolve(ROLLBACK));

        assertTrue(rollback.contains("DROP INDEX IF EXISTS calc.idx_calculation_version_trace"),
                "V044 rollback must drop the trace index");
    }

    @Test
    void verificationProbesTraceIndexReadOnly() throws IOException {
        String verification = read(repositoryRoot().resolve(VERIFICATION));

        assertTrue(verification.contains("idx_calculation_version_trace"),
                "V044 verification must probe the trace index");
        assertTrue(verification.contains("calc"));
        assertFalse(verification.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE|COMMIT)\\b.*"),
                "V044 verification must be read-only");
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
