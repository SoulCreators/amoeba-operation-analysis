package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileValidationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.SourceFileVersionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 源文件版本 JDBC 持久化适配器。
 *
 * <p>只追加不删除；生效指针切换通过「先失效、后生效」两条更新在调用方事务内完成，
 * 数据库侧由部分唯一索引 uk_source_file_version_active_object 兜底。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcSourceFileVersionRepository implements SourceFileVersionRepository {
    private static final String INSERT_SQL =
            "INSERT INTO flow.source_file_version (file_id, batch_no, period, dataset_code, "
                    + "object_code, version_no, template_code, template_version, file_hash, "
                    + "object_key, row_count, validation_status, active, uploaded_by, uploaded_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

    private static final String SELECT_COLUMNS =
            "id, file_id, batch_no, period, dataset_code, object_code, version_no, template_code, "
                    + "template_version, file_hash, object_key, row_count, validation_status, active, "
                    + "uploaded_by, uploaded_at";

    private final JdbcTemplate jdbc;

    public JdbcSourceFileVersionRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public SourceFileVersion save(SourceFileVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        Long id = jdbc.queryForObject(INSERT_SQL, Long.class,
                version.getFileId(), version.getBatchNo(), version.getPeriod(),
                version.getDatasetCode(), version.getObjectCode(), version.getVersionNo(),
                version.getTemplateCode(), version.getTemplateVersion(), version.getFileHash(),
                version.getObjectKey(), version.getRowCount(), version.getValidationStatus().name(),
                version.isActive(), version.getUploadedBy(), Timestamp.valueOf(version.getUploadedAt()));
        return SourceFileVersion.restore(id, version.getFileId(), version.getBatchNo(),
                version.getPeriod(), version.getDatasetCode(), version.getObjectCode(),
                version.getVersionNo(), version.getTemplateCode(), version.getTemplateVersion(),
                version.getFileHash(), version.getObjectKey(), version.getRowCount(),
                version.getValidationStatus(), version.isActive(), version.getUploadedBy(),
                version.getUploadedAt());
    }

    @Override
    public Optional<SourceFileVersion> findByFileId(String fileId) {
        List<SourceFileVersion> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM flow.source_file_version WHERE file_id = ?",
                (rs, rowNum) -> map(rs), requiredText(fileId, "fileId"));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public Optional<SourceFileVersion> findActive(String period, String objectCode) {
        List<SourceFileVersion> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM flow.source_file_version "
                        + "WHERE period = ? AND object_code = ? AND active = TRUE",
                (rs, rowNum) -> map(rs), requiredText(period, "period"),
                requiredText(objectCode, "objectCode"));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<SourceFileVersion> findByPeriodAndObject(String period, String objectCode) {
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM flow.source_file_version "
                        + "WHERE period = ? AND object_code = ? ORDER BY version_no",
                (rs, rowNum) -> map(rs), requiredText(period, "period"),
                requiredText(objectCode, "objectCode"));
    }

    @Override
    public int nextVersionNo(String period, String objectCode) {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) "
                        + "FROM flow.source_file_version WHERE period = ? AND object_code = ?",
                Integer.class, requiredText(period, "period"),
                requiredText(objectCode, "objectCode"));
        return max == null ? 0 : max;
    }

    @Override
    public boolean existsByObjectAndHash(String period, String objectCode, String fileHash) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM flow.source_file_version "
                        + "WHERE period = ? AND object_code = ? AND file_hash = ?",
                Integer.class, requiredText(period, "period"),
                requiredText(objectCode, "objectCode"), requiredText(fileHash, "fileHash"));
        return count != null && count > 0;
    }

    @Override
    public void switchActive(String period, String objectCode, String fileId) {
        String requiredPeriod = requiredText(period, "period");
        String requiredObject = requiredText(objectCode, "objectCode");
        String requiredFileId = requiredText(fileId, "fileId");
        // 先失效后生效，避免部分唯一索引在切换瞬间出现两个生效版本。
        jdbc.update("UPDATE flow.source_file_version SET active = FALSE "
                + "WHERE period = ? AND object_code = ? AND active = TRUE",
                requiredPeriod, requiredObject);
        jdbc.update("UPDATE flow.source_file_version SET active = TRUE WHERE file_id = ?",
                requiredFileId);
    }

    private SourceFileVersion map(ResultSet rs) throws SQLException {
        Timestamp uploaded = rs.getTimestamp("uploaded_at");
        LocalDateTime uploadedAt = uploaded == null ? LocalDateTime.now() : uploaded.toLocalDateTime();
        return SourceFileVersion.restore(rs.getLong("id"), rs.getString("file_id"),
                rs.getString("batch_no"), rs.getString("period"), rs.getString("dataset_code"),
                rs.getString("object_code"), rs.getInt("version_no"), rs.getString("template_code"),
                rs.getString("template_version"), rs.getString("file_hash"), rs.getString("object_key"),
                rs.getLong("row_count"),
                SourceFileValidationStatus.valueOf(rs.getString("validation_status")),
                rs.getBoolean("active"), rs.getString("uploaded_by"), uploadedAt);
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
