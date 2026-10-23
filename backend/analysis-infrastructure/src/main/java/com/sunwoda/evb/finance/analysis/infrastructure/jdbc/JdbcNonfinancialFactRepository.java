package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.TargetActual;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialFactRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 非财经指标事实 JDBC 持久化适配器。
 *
 * <p>同期整期替换为「先删除该期间全部事实，再逐条插入」；唯一键在数据库侧兜底重复阻断。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcNonfinancialFactRepository implements NonfinancialFactRepository {
    private static final String INSERT_SQL =
            "INSERT INTO flow.nonfinancial_fact (period, base_code, production_line, cell_model, "
                    + "quality_category, target_actual, quality_value, source_row_no, "
                    + "effective_file_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_COLUMNS =
            "id, period, base_code, production_line, cell_model, quality_category, "
                    + "target_actual, quality_value, source_row_no, effective_file_version";

    private final JdbcTemplate jdbc;

    public JdbcNonfinancialFactRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void replaceByPeriod(String period, List<NonfinancialFact> facts) {
        String required = requiredText(period, "period");
        jdbc.update("DELETE FROM flow.nonfinancial_fact WHERE period = ?", required);
        for (NonfinancialFact fact : facts) {
            jdbc.update(INSERT_SQL, fact.getPeriod(), fact.getBaseCode(), fact.getProductionLine(),
                    fact.getCellModel(), fact.getQualityCategory(), fact.getTargetActual().name(),
                    fact.getQualityValue(), fact.getSourceRowNo(), fact.getEffectiveFileVersion());
        }
    }

    @Override
    public List<NonfinancialFact> findByPeriod(String period) {
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM flow.nonfinancial_fact "
                        + "WHERE period = ? ORDER BY id",
                (rs, rowNum) -> map(rs), requiredText(period, "period"));
    }

    private NonfinancialFact map(ResultSet rs) throws SQLException {
        return new NonfinancialFact(rs.getLong("id"), rs.getString("period"),
                rs.getString("base_code"), rs.getString("production_line"),
                rs.getString("cell_model"), rs.getString("quality_category"),
                TargetActual.valueOf(rs.getString("target_actual")),
                rs.getBigDecimal("quality_value"), rs.getInt("source_row_no"),
                rs.getString("effective_file_version"));
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
