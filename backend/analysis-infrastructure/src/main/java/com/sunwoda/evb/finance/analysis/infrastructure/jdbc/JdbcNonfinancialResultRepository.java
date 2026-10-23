package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.PairingStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialResultRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 非财经指标配对结果 JDBC 持久化适配器。按期间整期替换结果。
 */
@Repository
@Primary
@Profile("db")
public class JdbcNonfinancialResultRepository implements NonfinancialResultRepository {
    private static final String INSERT_SQL =
            "INSERT INTO flow.nonfinancial_result (period, base_code, production_line, cell_model, "
                    + "quality_category, target_value, actual_value, gap, achievement_rate, "
                    + "pairing_status, leader_employee_no, leader_conflict) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_COLUMNS =
            "id, period, base_code, production_line, cell_model, quality_category, target_value, "
                    + "actual_value, gap, achievement_rate, pairing_status, leader_employee_no, "
                    + "leader_conflict";

    private final JdbcTemplate jdbc;

    public JdbcNonfinancialResultRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void replaceByPeriod(String period, List<NonfinancialResult> results) {
        String required = requiredText(period, "period");
        jdbc.update("DELETE FROM flow.nonfinancial_result WHERE period = ?", required);
        for (NonfinancialResult result : results) {
            jdbc.update(INSERT_SQL, result.getPeriod(), result.getBaseCode(),
                    result.getProductionLine(), result.getCellModel(), result.getQualityCategory(),
                    result.getTargetValue(), result.getActualValue(), result.getGap(),
                    result.getAchievementRate(), result.getPairingStatus().name(),
                    result.getLeaderEmployeeNo(), result.isLeaderConflict());
        }
    }

    @Override
    public List<NonfinancialResult> findByPeriod(String period) {
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM flow.nonfinancial_result "
                        + "WHERE period = ? ORDER BY id",
                (rs, rowNum) -> map(rs), requiredText(period, "period"));
    }

    private NonfinancialResult map(ResultSet rs) throws SQLException {
        return new NonfinancialResult(rs.getLong("id"), rs.getString("period"),
                rs.getString("base_code"), rs.getString("production_line"),
                rs.getString("cell_model"), rs.getString("quality_category"),
                rs.getBigDecimal("target_value"), rs.getBigDecimal("actual_value"),
                rs.getBigDecimal("gap"), rs.getBigDecimal("achievement_rate"),
                PairingStatus.valueOf(rs.getString("pairing_status")),
                rs.getString("leader_employee_no"), rs.getBoolean("leader_conflict"));
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
