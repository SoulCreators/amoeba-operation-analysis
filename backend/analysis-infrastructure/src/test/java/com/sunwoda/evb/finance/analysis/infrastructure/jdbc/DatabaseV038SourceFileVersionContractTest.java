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
 * SFV-V038：V038 源文件版本治理迁移、回滚和 verification 静态契约。
 *
 * <p>本次新增测试只验证数据库脚本引用的表、字段、约束、部分唯一索引与回滚范围，
 * 不测试或决定文件上传流程、生效切换时机或权限。</p>
 */
class DatabaseV038SourceFileVersionContractTest {
    private static final String MIGRATION = "database/migration/V038__create_source_file_version.sql";
    private static final String ROLLBACK = "database/rollback/V038__create_source_file_version.sql";
    private static final String VERIFICATION = "database/verification/check_source_file_version.sql";

    @Test
    void migrationCreatesVersionTableWithPartialUniqueIndexWithoutBusinessData() throws IOException {
        String migration = read(repositoryRoot().resolve(MIGRATION));
        String executableSql = stripSqlComments(migration);

        assertTrue(executableSql.contains("CREATE TABLE IF NOT EXISTS flow.source_file_version"));
        for (String column : Arrays.asList(
                "file_id VARCHAR(64) NOT NULL UNIQUE",
                "batch_no VARCHAR(64) NOT NULL",
                "period VARCHAR(7) NOT NULL",
                "object_code VARCHAR(64) NOT NULL",
                "version_no INTEGER NOT NULL",
                "file_hash VARCHAR(128) NOT NULL",
                "row_count BIGINT NOT NULL DEFAULT 0",
                "validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING'",
                "active BOOLEAN NOT NULL DEFAULT FALSE")) {
            assertTrue(executableSql.contains(column), "V038 must define " + column);
        }
        for (String constraint : Arrays.asList(
                "ck_source_file_version_row_count",
                "ck_source_file_version_validation_status",
                "fk_source_file_version_batch",
                "uk_source_file_version_object_version",
                "uk_source_file_version_active_object")) {
            assertTrue(executableSql.contains(constraint), "V038 must define " + constraint);
        }
        // 同对象最多一个生效版本由部分唯一索引兜底。
        assertTrue(executableSql.contains("WHERE active = TRUE"),
                "V038 must define a partial unique index on active");
        assertFalse(executableSql.contains("ON DELETE CASCADE"),
                "V038 must not cascade delete published lineage");
        assertFalse(executableSql.matches("(?is).*\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE)\\b.*"),
                "V038 migration must not write business data");
    }

    @Test
    void rollbackAndVerificationReferenceV038ObjectsTransactionally() throws IOException {
        Path root = repositoryRoot();
        String rollback = read(root.resolve(ROLLBACK));
        String verification = read(root.resolve(VERIFICATION));

        assertTrue(rollback.contains("DROP TABLE IF EXISTS flow.source_file_version"));
        assertFalse(rollback.contains("DROP SCHEMA"), "V038 rollback must not remove the shared flow schema");

        assertTrue(verification.contains("flow.source_file_version"));
        assertTrue(verification.contains("BEGIN;"));
        assertTrue(verification.contains("ROLLBACK;"));
        assertFalse(verification.contains("COMMIT;"), "V038 verification must not leave probe data committed");
        for (String constraint : Arrays.asList(
                "ck_source_file_version_row_count",
                "ck_source_file_version_validation_status",
                "fk_source_file_version_batch",
                "uk_source_file_version_object_version",
                "uk_source_file_version_active_object")) {
            assertTrue(verification.contains(constraint), "V038 verification must reference " + constraint);
        }
        assertTrue(verification.contains("unique_violation"),
                "V038 verification must probe the active-version partial unique index");
        assertTrue(verification.contains("foreign_key_violation"),
                "V038 verification must probe the batch foreign key");
        assertTrue(verification.contains("check_violation"),
                "V038 verification must probe database check constraints");
        assertTrue(verification.contains("indpred"),
                "V038 verification must assert the active index is partial");
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
