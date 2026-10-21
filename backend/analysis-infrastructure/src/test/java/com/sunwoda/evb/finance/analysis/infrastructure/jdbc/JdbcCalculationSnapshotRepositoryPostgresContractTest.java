package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V026 计算快照真实 PostgreSQL 往返契约。
 *
 * <p>本次新增：验证 JSONB 写入/读取、不可变冲突不覆盖、数据库类型约束和
 * calculation_version 到 calculation_snapshot 的外键；不接入普通计算或 D4 写集。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcCalculationSnapshotRepositoryPostgresContractTest {
    private static final String MIGRATION_FILE =
            "V026__create_calculation_version_snapshot_outbox.sql";
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcCalculationSnapshotRepository repository;
    private String snapshotId;

    @BeforeAll
    void connectToConfiguredPostgres() {
        String url = requiredNonBlankEnvironment("AMOEBA_DB_URL");
        String username = requiredNonBlankEnvironment("AMOEBA_DB_USERNAME");
        String password = requiredPresentEnvironment("AMOEBA_DB_PASSWORD");
        if (!migrationPresent()) {
            throw new IllegalStateException("缺少数据库迁移文件: database/migration/"
                    + MIGRATION_FILE);
        }
        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.queryForObject("SELECT 1", Integer.class);
        Integer columns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = 'calc'"
                        + " AND table_name = 'calculation_snapshot'"
                        + " AND column_name IN ('snapshot_id', 'snapshot_type', 'schema_version',"
                        + " 'content_json', 'source_version_set', 'content_hash',"
                        + " 'source_version_hash', 'rule_version', 'created_by', 'created_at')",
                Integer.class);
        assertEquals(10, columns, "V026 快照表十个字段必须全部存在");
        repository = new JdbcCalculationSnapshotRepository(dataSource);
    }

    @BeforeEach
    void createIsolatedFixtureId() {
        snapshotId = "PG-SNAPSHOT-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void removeFixture() {
        if (jdbc != null && snapshotId != null) {
            jdbc.update("DELETE FROM calc.calculation_snapshot WHERE snapshot_id = ?",
                    snapshotId);
        }
    }

    @Test
    void roundTripJsonbAndImmutableConflictPreserveTheFirstSnapshot() {
        CalculationSnapshot first = snapshot("{\"mapping\":\"v1\"}",
                "[\"MAP-001\",\"RULE-001\"]", "HASH-FIRST");

        repository.save(first);
        Optional<CalculationSnapshot> found = repository.findById(snapshotId);

        assertTrue(found.isPresent());
        assertTrue(found.get().getContentJson().contains("\"mapping\""));
        assertTrue(found.get().getSourceVersionSetJson().contains("MAP-001"));
        assertEquals("jsonb", jdbc.queryForObject(
                "SELECT pg_typeof(content_json)::text FROM calc.calculation_snapshot"
                        + " WHERE snapshot_id = ?", String.class, snapshotId));
        assertEquals("array", jdbc.queryForObject(
                "SELECT jsonb_typeof(source_version_set) FROM calc.calculation_snapshot"
                        + " WHERE snapshot_id = ?", String.class, snapshotId));

        // 本次新增：重复保存同一主键不得覆盖不可变快照的首个内容和哈希。
        repository.save(snapshot("{\"mapping\":\"changed\"}",
                "[\"MAP-CHANGED\"]", "HASH-CHANGED"));
        CalculationSnapshot replay = repository.findById(snapshotId).get();
        assertTrue(replay.getContentJson().contains("\"mapping\""));
        assertTrue(!replay.getContentJson().contains("changed"));
        assertEquals("HASH-FIRST", replay.getContentHash());
    }

    @Test
    void migrationKeepsJsonbChecksAndCalculationVersionForeignKey() {
        Integer foreignKeys = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint child_fk"
                        + " JOIN pg_class child ON child.oid = child_fk.conrelid"
                        + " JOIN pg_namespace child_ns ON child_ns.oid = child.relnamespace"
                        + " JOIN pg_class parent ON parent.oid = child_fk.confrelid"
                        + " JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace"
                        + " WHERE child_ns.nspname = 'calc'"
                        + " AND child.relname = 'calculation_version'"
                        + " AND parent_ns.nspname = 'calc'"
                        + " AND parent.relname = 'calculation_snapshot'"
                        + " AND child_fk.contype = 'f'", Integer.class);
        assertEquals(1, foreignKeys, "计算版本必须引用计算快照");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO calc.calculation_snapshot (snapshot_id, snapshot_type,"
                        + " schema_version, content_json, source_version_set, content_hash,"
                        + " created_by) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                snapshotId, "INPUT", "V026-1", "[\"not-object\"]", "[]",
                "HASH-BAD-CONTENT", "postgres-contract"));
    }

    private CalculationSnapshot snapshot(String contentJson, String sourceVersionSet,
                                         String contentHash) {
        return new CalculationSnapshot(snapshotId, "INPUT", "V026-1", contentJson,
                sourceVersionSet, contentHash, "HASH-SOURCE", "RULE-2026",
                "postgres-contract", LocalDateTime.of(2026, 10, 21, 15, 10));
    }

    private static boolean migrationPresent() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("database/migration").resolve(MIGRATION_FILE))) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static String requiredNonBlankEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少 PostgreSQL 契约环境变量: " + name);
        }
        return value.trim();
    }

    /** 密码允许为空，只有未注入环境变量时才判定为配置缺失。 */
    private static String requiredPresentEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("缺少 PostgreSQL 契约环境变量: " + name);
        }
        return value;
    }
}
