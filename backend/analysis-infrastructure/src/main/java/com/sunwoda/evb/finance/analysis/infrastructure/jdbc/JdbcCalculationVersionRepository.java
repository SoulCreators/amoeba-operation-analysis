package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * V026 计算结果版本头的 PostgreSQL JDBC 仓储。
 *
 * <p>本次新增：复用 V026 的 calculation_version 表，提供版本保存、当前版本查询和历史版本查询；
 * 只负责版本头持久化，不复制 pnl_result 明细，也不改变人工修订事务编排。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationVersionRepository implements CalculationVersionRepository {
    private static final String VERSION_COLUMNS =
            "v.calculation_version_id, v.batch_no, v.perspective, v.version_no, v.job_id, "
                    + "v.snapshot_id, v.source_version_hash, v.config_hash, v.input_fingerprint, "
                    + "v.status, v.current_flag, v.supersedes_version_id, v.trace_id, v.calculated_by, "
                    + "v.calculated_at, v.confirmed_by, v.confirmed_at, v.published_by, v.published_at";

    private final JdbcTemplate jdbc;

    public JdbcCalculationVersionRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 本次新增：先解析 V026 版本头所需的 batch_id，再按版本主键幂等保存全部版本字段；
     * 当前版本唯一性继续交给 V026 的部分唯一索引兜底，避免仓储静默覆盖其他当前版本。
     */
    @Override
    @Transactional
    public CalculationVersion save(CalculationVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        Long batchId = jdbc.queryForObject(
                "SELECT id FROM meta.analysis_batch WHERE batch_no = ?", Long.class, version.getBatchNo());
        jdbc.update("INSERT INTO calc.calculation_version ("
                        + "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, "
                        + "snapshot_id, source_version_hash, config_hash, input_fingerprint, status, "
                        + "current_flag, supersedes_version_id, trace_id, calculated_by, calculated_at, "
                        + "confirmed_by, confirmed_at, published_by, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (calculation_version_id) DO UPDATE SET "
                        + "batch_id = EXCLUDED.batch_id, batch_no = EXCLUDED.batch_no, "
                        + "perspective = EXCLUDED.perspective, version_no = EXCLUDED.version_no, "
                        + "job_id = EXCLUDED.job_id, snapshot_id = EXCLUDED.snapshot_id, "
                        + "source_version_hash = EXCLUDED.source_version_hash, config_hash = EXCLUDED.config_hash, "
                        + "input_fingerprint = EXCLUDED.input_fingerprint, status = EXCLUDED.status, "
                        + "current_flag = EXCLUDED.current_flag, "
                        + "supersedes_version_id = EXCLUDED.supersedes_version_id, trace_id = EXCLUDED.trace_id, "
                        + "calculated_by = EXCLUDED.calculated_by, calculated_at = EXCLUDED.calculated_at, "
                        + "confirmed_by = EXCLUDED.confirmed_by, confirmed_at = EXCLUDED.confirmed_at, "
                        + "published_by = EXCLUDED.published_by, published_at = EXCLUDED.published_at",
                version.getCalculationVersionId(), batchId, version.getBatchNo(),
                version.getPerspective().getCode(), version.getVersionNo(), version.getJobId(),
                version.getSnapshotId(), version.getSourceVersionHash(), version.getConfigHash(),
                version.getInputFingerprint(), version.getStatus().name(), version.isCurrent(),
                version.getSupersedesVersionId(), version.getTraceId(), version.getCalculatedBy(),
                timestamp(version.getCalculatedAt()), version.getConfirmedBy(), timestamp(version.getConfirmedAt()),
                version.getPublishedBy(), timestamp(version.getPublishedAt()));
        return version;
    }

    @Override
    public Optional<CalculationVersion> findById(String calculationVersionId) {
        String id = required(calculationVersionId, "calculationVersionId");
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM calc.calculation_version v "
                        + "WHERE v.calculation_version_id = ?", this::one, id);
    }

    @Override
    public Optional<CalculationVersion> findCurrentByBatchNo(String batchNo,
                                                              AnalysisPerspective perspective) {
        String normalizedBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective 不能为空");
        }
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM calc.calculation_version v "
                        + "JOIN meta.analysis_batch b ON b.id = v.batch_id "
                        + "WHERE b.batch_no = ? AND v.perspective = ? AND v.current_flag = TRUE "
                        + "ORDER BY v.calculated_at DESC, v.calculation_version_id DESC LIMIT 1",
                this::one, normalizedBatchNo, perspective.getCode());
    }

    @Override
    public List<CalculationVersion> findByBatchNo(String batchNo, AnalysisPerspective perspective) {
        String normalizedBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective 不能为空");
        }
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM calc.calculation_version v "
                        + "JOIN meta.analysis_batch b ON b.id = v.batch_id "
                        + "WHERE b.batch_no = ? AND v.perspective = ? "
                        + "ORDER BY v.calculated_at, v.version_no, v.calculation_version_id",
                (rs, rowNum) -> map(rs), normalizedBatchNo, perspective.getCode());
    }

    @Override
    public Optional<CalculationVersion> findByTraceId(String traceId) {
        String normalizedTraceId = required(traceId, "traceId");
        return jdbc.query("SELECT " + VERSION_COLUMNS + " FROM calc.calculation_version v "
                        + "WHERE v.trace_id = ? "
                        + "ORDER BY v.calculated_at DESC, v.calculation_version_id DESC LIMIT 1",
                this::one, normalizedTraceId);
    }

    private Optional<CalculationVersion> one(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        return Optional.of(map(rs));
    }

    private CalculationVersion map(ResultSet rs) throws SQLException {
        return new CalculationVersion(
                rs.getString("calculation_version_id"),
                rs.getString("batch_no"),
                AnalysisPerspective.fromCode(rs.getString("perspective")),
                rs.getString("version_no"),
                rs.getString("job_id"),
                rs.getString("snapshot_id"),
                rs.getString("source_version_hash"),
                rs.getString("config_hash"),
                rs.getString("input_fingerprint"),
                CalculationVersionStatus.valueOf(rs.getString("status")),
                rs.getBoolean("current_flag"),
                rs.getString("supersedes_version_id"),
                rs.getString("trace_id"),
                rs.getString("calculated_by"),
                localDateTime(rs.getTimestamp("calculated_at")),
                rs.getString("confirmed_by"),
                localDateTime(rs.getTimestamp("confirmed_at")),
                rs.getString("published_by"),
                localDateTime(rs.getTimestamp("published_at")));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
