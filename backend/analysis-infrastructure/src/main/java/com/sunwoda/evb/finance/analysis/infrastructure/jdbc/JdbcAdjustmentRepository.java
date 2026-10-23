package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.repository.AdjustmentRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 调整单 JDBC 持久化适配器：按自然键 upsert，支持按期间与生效状态查询。 */
@Repository
@Primary
@Profile("db")
public class JdbcAdjustmentRepository implements AdjustmentRepository {
    private static final String SELECT_COLUMNS =
            "adjustment_id, period, perspective, scope_code, scenario, metric_code, amount, "
                    + "direction, reason, evidence_file_id, expected_version, entered_by, status, "
                    + "entered_at, voided_by, voided_at, void_reason";

    private final JdbcTemplate jdbc;

    public JdbcAdjustmentRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public synchronized Adjustment save(Adjustment adjustment) {
        jdbc.update("INSERT INTO calc.adjustment (adjustment_id, period, perspective, scope_code, "
                        + "scenario, metric_code, amount, direction, reason, evidence_file_id, "
                        + "expected_version, entered_by, status, entered_at, voided_by, voided_at, void_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (adjustment_id) DO UPDATE SET "
                        + "scenario = EXCLUDED.scenario, amount = EXCLUDED.amount, "
                        + "direction = EXCLUDED.direction, reason = EXCLUDED.reason, "
                        + "status = EXCLUDED.status, voided_by = EXCLUDED.voided_by, "
                        + "voided_at = EXCLUDED.voided_at, void_reason = EXCLUDED.void_reason",
                adjustment.getAdjustmentId(), adjustment.getPeriod(),
                adjustment.getPerspective().getCode(), adjustment.getScopeCode(),
                adjustment.getScenario().getCode(), adjustment.getMetricCode(),
                adjustment.getAmount(), adjustment.getDirection().name(),
                adjustment.getReason(), adjustment.getEvidenceFileId(),
                adjustment.getExpectedVersion(), adjustment.getEnteredBy(),
                adjustment.getStatus().name(), toTimestamp(adjustment.getEnteredAt()),
                adjustment.getVoidedBy(), toTimestamp(adjustment.getVoidedAt()),
                adjustment.getVoidReason());
        return adjustment;
    }

    @Override
    public Optional<Adjustment> findById(String adjustmentId) {
        List<Adjustment> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM calc.adjustment WHERE adjustment_id = ?",
                (rs, row) -> map(rs), adjustmentId);
        return result.isEmpty() ? Optional.<Adjustment>empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Adjustment> findByPeriod(String period) {
        List<Adjustment> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM calc.adjustment WHERE period = ? ORDER BY entered_at, adjustment_id",
                (rs, row) -> map(rs), period);
        return result == null ? Collections.<Adjustment>emptyList() : result;
    }

    @Override
    public List<Adjustment> findActive(String period, AnalysisPerspective perspective) {
        List<Adjustment> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM calc.adjustment "
                        + "WHERE period = ? AND perspective = ? AND status = 'ACTIVE' "
                        + "ORDER BY entered_at, adjustment_id",
                (rs, row) -> map(rs), period, perspective.getCode());
        return result == null ? Collections.<Adjustment>emptyList() : result;
    }

    private Adjustment map(ResultSet rs) throws SQLException {
        return Adjustment.restore(
                rs.getString("adjustment_id"), rs.getString("period"),
                AnalysisPerspective.fromCode(rs.getString("perspective")),
                rs.getString("scope_code"), AdjustmentScenario.fromCode(rs.getString("scenario")),
                rs.getString("metric_code"), rs.getBigDecimal("amount"),
                AdjustmentDirection.valueOf(rs.getString("direction")),
                rs.getString("reason"), rs.getString("evidence_file_id"),
                rs.getString("expected_version"), rs.getString("entered_by"),
                AdjustmentStatus.valueOf(rs.getString("status")),
                toLocalDateTime(rs.getTimestamp("entered_at")),
                rs.getString("voided_by"), toLocalDateTime(rs.getTimestamp("voided_at")),
                rs.getString("void_reason"));
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
