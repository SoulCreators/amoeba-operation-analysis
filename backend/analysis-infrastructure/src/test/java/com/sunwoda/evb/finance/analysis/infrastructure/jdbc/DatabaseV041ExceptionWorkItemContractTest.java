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
 * NF-V041：V041 异常工作台工作项表迁移、回滚和 verification 静态契约。
 */
class DatabaseV041ExceptionWorkItemContractTest {
    private static final String MIGRATION = "database/migration/V041__create_exception_work_item.sql";
    private static final String ROLLBACK = "database/rollback/V041__create_exception_work_item.sql";
    private static final String VERIFICATION = "database/verification/check_exception_work_item.sql";

    @Test
    void migrationCreatesWorkItemTableWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.exception_work_item"));
        for (String column : Arrays.asList(
                "id BIGSERIAL PRIMARY KEY",
                "task_no VARCHAR(64) NOT NULL",
                "object_code VARCHAR(64) NOT NULL",
                "issue_code VARCHAR(64) NOT NULL",
                "severity VARCHAR(16) NOT NULL DEFAULT 'BLOCKING'",
                "status VARCHAR(16) NOT NULL DEFAULT 'OPEN'",
                "ignored_by VARCHAR(64)",
                "ignored_at TIMESTAMP",
                "ignore_reason VARCHAR(1000)")) {
            assertTrue(executableSql.contains(column), "V041 must define " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_exception_work_item_severity",
                "ck_exception_work_item_status")) {
            assertTrue(executableSql.contains(constraint), "V041 must define " + constraint);
        }
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V041 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV041ObjectsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.exception_work_item"));
        assertFalse(rollback.contains("DROP SCHEMA"), "V041 rollback must not remove the shared flow schema");

        assertTrue(verification.contains("flow.exception_work_item"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"), "V041 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "ck_exception_work_item_severity",
                "ck_exception_work_item_status")) {
            assertTrue(verification.contains(constraint), "V041 verification must reference " + constraint);
        }
        assertTrue(verification.contains("check_violation"),
                "V041 verification must probe database check constraints");
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
