package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcMetricDefinitionRepository implements MetricDefinitionRepository {
    private final JdbcTemplate jdbc;

    public JdbcMetricDefinitionRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public MetricDefinition save(MetricDefinition definition) {
        Optional<MetricDefinition> existing = findByCode(definition.getMetricCode());
        if (existing.isPresent()) {
            jdbc.update("UPDATE cfg.metric_definition SET metric_name = ?, applicable_perspective = ?, unit = ?, "
                            + "formula_expression = ?, display_order = ?, enabled = ?, ai_summary_template = ?, "
                            + "updated_by = ?, updated_at = ? WHERE metric_code = ?",
                    definition.getMetricName(), definition.getApplicablePerspective(), definition.getUnit(),
                    definition.getFormulaExpression(), definition.getDisplayOrder(), definition.isEnabled(),
                    definition.getAiSummaryTemplate(), definition.getUpdatedBy(), Timestamp.valueOf(now()),
                    definition.getMetricCode());
            return definition;
        }
        jdbc.update("INSERT INTO cfg.metric_definition(metric_code, metric_name, applicable_perspective, unit, "
                        + "formula_expression, display_order, enabled, ai_summary_template, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", definition.getMetricCode(), definition.getMetricName(),
                definition.getApplicablePerspective(), definition.getUnit(), definition.getFormulaExpression(),
                definition.getDisplayOrder(), definition.isEnabled(), definition.getAiSummaryTemplate(),
                definition.getUpdatedBy(), definition.getUpdatedBy());
        return definition;
    }

    @Override
    public Optional<MetricDefinition> findByCode(String metricCode) {
        return jdbc.query("SELECT id, metric_code, metric_name, applicable_perspective, unit, formula_expression, "
                        + "display_order, enabled, ai_summary_template, updated_by, updated_at "
                        + "FROM cfg.metric_definition WHERE metric_code = ?", rs -> {
                    if (!rs.next()) return Optional.<MetricDefinition>empty();
                    return Optional.of(metric(rs));
                }, metricCode);
    }

    @Override
    public List<MetricDefinition> list(String perspective, boolean includeDisabled) {
        String sql = "SELECT id, metric_code, metric_name, applicable_perspective, unit, formula_expression, "
                + "display_order, enabled, ai_summary_template, updated_by, updated_at FROM cfg.metric_definition "
                + "WHERE (? IS NULL OR applicable_perspective = 'ALL' OR applicable_perspective = ?) "
                + "AND (? OR enabled = TRUE) ORDER BY display_order, metric_code";
        return jdbc.query(sql, (rs, row) -> metric(rs), perspective, perspective, includeDisabled);
    }

    private MetricDefinition metric(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new MetricDefinition(rs.getLong("id"), rs.getString("metric_code"), rs.getString("metric_name"),
                rs.getString("applicable_perspective"), rs.getString("unit"), rs.getString("formula_expression"),
                rs.getInt("display_order"), rs.getBoolean("enabled"), rs.getString("ai_summary_template"),
                rs.getString("updated_by"), updated == null ? null : updated.toLocalDateTime());
    }

    private LocalDateTime now() { return LocalDateTime.now(); }
}
