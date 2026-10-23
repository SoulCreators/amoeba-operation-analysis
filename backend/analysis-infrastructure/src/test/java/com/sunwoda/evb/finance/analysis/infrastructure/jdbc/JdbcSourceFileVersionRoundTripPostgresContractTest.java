package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileValidationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SFV-JDBC-001：源文件版本治理的真实 PostgreSQL 往返契约。
 *
 * <p>本次新增测试验证源文件版本表只追加不删除、生效指针切换与版本号递增，并在测试结束
 * 后按先子表后父表顺序清理夹具。</p>
 */
class JdbcSourceFileVersionRoundTripPostgresContractTest {
    private static final String PERIOD = "2026-10";

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcSourceFileVersionRepository repository;

    private String fixturePrefix;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; source-file-version PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; source-file-version PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; source-file-version PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT id, file_id, batch_no, period, dataset_code, object_code, "
                    + "version_no, template_code, template_version, file_hash, object_key, "
                    + "row_count, validation_status, active, uploaded_by, uploaded_at "
                    + "FROM flow.source_file_version LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: flow.source_file_version is not available: "
                    + failure.getMessage());
        }
        repository = new JdbcSourceFileVersionRepository(dataSource);
    }

    @BeforeEach
    void createFixturePrefix() {
        fixturePrefix = "SFV-JDBC-001-" + UUID.randomUUID().toString().replace("-", "");
        String batchNo = batchNo();
        jdbc.update("INSERT INTO meta.analysis_batch (batch_no, period, status, created_by) "
                + "VALUES (?, ?, 'DRAFT', 'verification')", batchNo, PERIOD);
    }

    @AfterEach
    void cleanupFixtureRows() {
        if (jdbc == null || fixturePrefix == null) {
            return;
        }
        String pattern = fixturePrefix + "%";
        jdbc.update("DELETE FROM flow.source_file_version WHERE batch_no LIKE ?", pattern);
        jdbc.update("DELETE FROM meta.analysis_batch WHERE batch_no LIKE ?", pattern);
    }

    @Test
    void saveAndFindRoundTripAllFields() {
        SourceFileVersion input = version(1, "hash-1", "key-1", 120L, "u-1");
        SourceFileVersion saved = repository.save(input);

        assertNotNull(saved.getId());
        Optional<SourceFileVersion> loaded = repository.findByFileId(saved.getFileId());
        assertTrue(loaded.isPresent());
        assertEquals(input.getFileId(), loaded.get().getFileId());
        assertEquals(PERIOD, loaded.get().getPeriod());
        assertEquals(input.getObjectCode(), loaded.get().getObjectCode());
        assertEquals(1, loaded.get().getVersionNo());
        assertEquals("hash-1", loaded.get().getFileHash());
        assertEquals(120L, loaded.get().getRowCount());
        assertEquals(SourceFileValidationStatus.PENDING, loaded.get().getValidationStatus());
        assertFalse(loaded.get().isActive());
        assertEquals("u-1", loaded.get().getUploadedBy());
    }

    @Test
    void switchActiveDeactivatesOldAndRetainsHistory() {
        SourceFileVersion v1 = repository.save(version(1, "hash-1", "key-1", 120L, "u-1"));
        SourceFileVersion v2 = repository.save(version(2, "hash-2", "key-2", 220L, "u-1"));

        repository.switchActive(PERIOD, v1.getObjectCode(), v2.getFileId());

        Optional<SourceFileVersion> active = repository.findActive(PERIOD, v1.getObjectCode());
        assertTrue(active.isPresent());
        assertEquals(v2.getFileId(), active.get().getFileId());
        assertTrue(active.get().isActive());

        List<SourceFileVersion> history = repository.findByPeriodAndObject(PERIOD, v1.getObjectCode());
        assertEquals(2, history.size());
        assertFalse(history.get(0).isActive());
        assertEquals(v1.getVersionNo(), history.get(0).getVersionNo());
        assertEquals(v2.getVersionNo(), history.get(1).getVersionNo());
    }

    @Test
    void nextVersionNoAndHashExistenceTrackObjectVersions() {
        repository.save(version(1, "hash-1", "key-1", 120L, "u-1"));
        repository.save(version(2, "hash-2", "key-2", 220L, "u-1"));

        assertEquals(2, repository.nextVersionNo(PERIOD, objectCode()));
        assertTrue(repository.existsByObjectAndHash(PERIOD, objectCode(), "hash-1"));
        assertFalse(repository.existsByObjectAndHash(PERIOD, objectCode(), "hash-missing"));
    }

    private SourceFileVersion version(int versionNo, String hash, String key, long rowCount, String uploadedBy) {
        return SourceFileVersion.create(fileId(versionNo), batchNo(), PERIOD, datasetCode(),
                objectCode(), versionNo, "TPL-1", "v1", hash, key, rowCount, uploadedBy);
    }

    private String batchNo() {
        return fixturePrefix + "-batch";
    }

    private String datasetCode() {
        return fixturePrefix + "-ds";
    }

    private String objectCode() {
        return fixturePrefix + "-obj";
    }

    private String fileId(int versionNo) {
        return fixturePrefix + "-file-" + versionNo;
    }
}
