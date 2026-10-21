package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * V026 计算输入快照的 PostgreSQL JDBC 仓储。
 *
 * <p>本次新增：将不可变快照的普通字段和两个 JSONB 字段映射到
 * {@code calc.calculation_snapshot}，只在 db Profile 提供生产实现；领域层继续只接收 JSON 文本。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationSnapshotRepository implements CalculationSnapshotRepository {
    private static final String SNAPSHOT_COLUMNS =
            "snapshot_id, snapshot_type, schema_version, content_json, source_version_set, "
                    + "content_hash, source_version_hash, rule_version, created_by, created_at";

    private static final String SAVE_SQL =
            "INSERT INTO calc.calculation_snapshot (" + SNAPSHOT_COLUMNS + ") "
                    + "VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (snapshot_id) DO NOTHING";

    private final JdbcTemplate jdbc;

    // 本次修改：显式标记 DataSource 构造函数，确保 db Profile 下由 Spring 正确注入 JDBC 数据源。
    @Autowired
    public JdbcCalculationSnapshotRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    JdbcCalculationSnapshotRepository(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc 不能为空");
        }
        this.jdbc = jdbc;
    }

    @Override
    public CalculationSnapshot save(CalculationSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }
        // 本次新增：通过 PostgreSQL jsonb 显式转换保存两段 JSON，避免依赖驱动的隐式类型推断。
        jdbc.update(SAVE_SQL, snapshot.getSnapshotId(), snapshot.getSnapshotType(),
                snapshot.getSchemaVersion(), snapshot.getContentJson(), snapshot.getSourceVersionSetJson(),
                snapshot.getContentHash(), snapshot.getSourceVersionHash(), snapshot.getRuleVersion(),
                snapshot.getCreatedBy(), timestamp(snapshot.getCreatedAt()));
        return snapshot;
    }

    @Override
    public Optional<CalculationSnapshot> findById(String snapshotId) {
        String id = required(snapshotId, "snapshotId");
        return jdbc.query("SELECT " + SNAPSHOT_COLUMNS + " FROM calc.calculation_snapshot "
                        + "WHERE snapshot_id = ?", this::one, id);
    }

    private Optional<CalculationSnapshot> one(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        return Optional.of(map(rs));
    }

    private CalculationSnapshot map(ResultSet rs) throws SQLException {
        return new CalculationSnapshot(
                rs.getString("snapshot_id"),
                rs.getString("snapshot_type"),
                rs.getString("schema_version"),
                rs.getString("content_json"),
                rs.getString("source_version_set"),
                rs.getString("content_hash"),
                rs.getString("source_version_hash"),
                rs.getString("rule_version"),
                rs.getString("created_by"),
                localDateTime(rs.getTimestamp("created_at")));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
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
