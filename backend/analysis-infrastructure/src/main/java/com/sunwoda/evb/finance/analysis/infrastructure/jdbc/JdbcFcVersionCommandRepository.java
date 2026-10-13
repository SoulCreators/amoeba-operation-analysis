package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.FcVersionCommandRepository;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** FC 版本和快照的事务写入实现；正式版本由数据库唯一索引保证同一口径只有一个。 */
@Repository
@Profile("db")
public class JdbcFcVersionCommandRepository implements FcVersionCommandRepository {
    private final JdbcTemplate jdbc;

    public JdbcFcVersionCommandRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public FcVersion create(String versionCode, AnalysisPerspective perspective, String basePeriod,
                            String coverageStart, String coverageEnd, String operator) {
        try {
            jdbc.update("INSERT INTO calc.fc_version(version_code, perspective, base_period, coverage_start, "
                            + "coverage_end, status, uploaded_by, official) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, FALSE)",
                    versionCode, perspective.getCode(), basePeriod, coverageStart, coverageEnd, operator);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("FC版本已存在: " + versionCode, ex);
        }
        return find(versionCode, perspective, basePeriod)
                .orElseThrow(() -> new IllegalStateException("FC版本创建后无法读取: " + versionCode));
    }

    @Override
    public Optional<FcVersion> find(String versionCode, AnalysisPerspective perspective, String basePeriod) {
        try {
            FcVersion value = jdbc.queryForObject("SELECT version_code, perspective, base_period, coverage_start, "
                            + "coverage_end, status, uploaded_by, uploaded_at, official, published_by, published_at "
                            + "FROM calc.fc_version WHERE version_code = ? AND perspective = ? AND base_period = ?",
                    (rs, row) -> map(rs), versionCode, perspective.getCode(), basePeriod);
            return Optional.ofNullable(value);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public FcVersion publish(String versionCode, AnalysisPerspective perspective, String basePeriod,
                             String operator) {
        Integer conflict = jdbc.queryForObject("SELECT COUNT(1) FROM calc.fc_version "
                        + "WHERE perspective = ? AND base_period = ? AND official = TRUE AND version_code <> ?",
                Integer.class, perspective.getCode(), basePeriod, versionCode);
        if (conflict != null && conflict.intValue() > 0) {
            throw new IllegalStateException("同一视角和基准期间已有正式 FC 版本，请先明确替换策略");
        }
        List<FcVersion> values = jdbc.query("UPDATE calc.fc_version SET status = 'PUBLISHED', official = TRUE, "
                        + "published_by = ?, published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE version_code = ? AND perspective = ? AND base_period = ? RETURNING "
                        + "version_code, perspective, base_period, coverage_start, coverage_end, status, uploaded_by, "
                        + "uploaded_at, official, published_by, published_at", (rs, row) -> map(rs),
                operator, versionCode, perspective.getCode(), basePeriod);
        if (values.isEmpty()) throw new IllegalArgumentException("FC版本不存在: " + versionCode);
        return values.get(0);
    }

    @Override
    public int upsertSnapshots(String versionCode, AnalysisPerspective perspective, String basePeriod,
                               final List<FcSnapshot> snapshots, final String sourceTaskNo) {
        final Long versionId = jdbc.queryForObject("SELECT id FROM calc.fc_version WHERE version_code = ? "
                + "AND perspective = ? AND base_period = ?", Long.class,
                versionCode, perspective.getCode(), basePeriod);
        if (versionId == null) throw new IllegalArgumentException("FC版本不存在: " + versionCode);
        int[] counts = jdbc.batchUpdate("INSERT INTO calc.fc_snapshot(fc_version_id, period, scope_code, "
                        + "metric_code, metric_value, source_task_no) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (fc_version_id, period, scope_code, metric_code) DO UPDATE SET "
                        + "metric_value = EXCLUDED.metric_value, source_task_no = EXCLUDED.source_task_no, "
                        + "updated_at = CURRENT_TIMESTAMP", new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        FcSnapshot snapshot = snapshots.get(i);
                        ps.setLong(1, versionId.longValue());
                        ps.setString(2, snapshot.getPeriod());
                        ps.setString(3, snapshot.getScopeCode());
                        ps.setString(4, snapshot.getMetricCode());
                        ps.setBigDecimal(5, snapshot.getValue());
                        ps.setString(6, sourceTaskNo);
                    }

                    @Override
                    public int getBatchSize() { return snapshots.size(); }
                });
        int total = 0;
        for (int count : counts) total += count < 0 ? 0 : count;
        return total;
    }

    private FcVersion map(ResultSet rs) throws SQLException {
        return new FcVersion(rs.getString("version_code"), AnalysisPerspective.fromCode(rs.getString("perspective")),
                rs.getString("base_period"), rs.getString("coverage_start"), rs.getString("coverage_end"),
                rs.getString("status"), rs.getString("uploaded_by"), localDateTime(rs.getTimestamp("uploaded_at")),
                rs.getBoolean("official"), rs.getString("published_by"),
                localDateTime(rs.getTimestamp("published_at")));
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
