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
 * NF-V040：V040 非财经指标发布记录表迁移、回滚和 verification 静态契约。
 */
class DatabaseV040NonfinancialPublicationContractTest {
    private static final String MIGRATION = "database/migration/V040__create_nonfinancial_publication.sql";
    private static final String ROLLBACK = "database/rollback/V040__create_nonfinancial_publication.sql";
    private static final String VERIFICATION = "database/verification/check_nonfinancial_publication.sql";

    @Test
    void migrationCreatesPublicationTableWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.nonfinancial_publication"));
        for (String column : Arrays.asList(
                "period VARCHAR(7) PRIMARY KEY",
                "status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'",
                "conflict_count INTEGER NOT NULL DEFAULT 0",
                "published_by VARCHAR(64)",
                "published_at TIMESTAMP")) {
            assertTrue(executableSql.contains(column), "V040 must define " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_nonfinancial_publication_status",
                "ck_nonfinancial_publication_conflict_count")) {
            assertTrue(executableSql.contains(constraint), "V040 must define " + constraint);
        }
        assertFalse(executableSql.contains("ON DELETE CASCADE"),
                "V040 must not cascade delete publication records");
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V040 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV040ObjectsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.nonfinancial_publication"));
        assertFalse(rollback.contains("DROP SCHEMA"), "V040 rollback must not remove the shared flow schema");

        assertTrue(verification.contains("flow.nonfinancial_publication"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"), "V040 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "ck_nonfinancial_publication_status",
                "ck_nonfinancial_publication_conflict_count")) {
            assertTrue(verification.contains(constraint), "V040 verification must reference " + constraint);
        }
        assertTrue(verification.contains("unique_violation"),
                "V040 verification must probe the period uniqueness");
        assertTrue(verification.contains("check_violation"),
                "V040 verification must probe database check constraints");
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
