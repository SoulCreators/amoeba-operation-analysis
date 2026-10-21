package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
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

/**
 * 人工失败处理动作 JDBC 仓储。
 *
 * <p>本次新增：人工入口采用追加写审计记录，保留操作者、原因、原尝试号和处理结果。</p>
 * <p>本次补充：db profile 下标记为首选实现，与默认内存动作仓储并存时不产生注入歧义。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationRetryActionRepository implements CalculationRetryActionRepository {
    private final JdbcTemplate jdbc;

    public JdbcCalculationRetryActionRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource 不能为空");
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public CalculationRetryAction save(CalculationRetryAction action) {
        if (action == null) throw new IllegalArgumentException("action 不能为空");
        // 本次新增：动作记录只 INSERT，不允许用后续人工处理覆盖历史动作。
        Long id = jdbc.queryForObject("INSERT INTO flow.calculation_retry_action "
                        + "(job_id, request_key, action_type, operator, reason, original_attempt_no, "
                        + "result, result_detail, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "RETURNING action_id", Long.class, action.getJobId(), action.getRequestKey(),
                action.getType().name(), action.getOperator(), action.getReason(), action.getOriginalAttemptNo(),
                action.getResult(), action.getResultDetail(), Timestamp.valueOf(action.getOccurredAt()));
        return new CalculationRetryAction(id == null ? 0L : id, action.getJobId(), action.getRequestKey(),
                action.getType(), action.getOperator(), action.getReason(), action.getOriginalAttemptNo(),
                action.getResult(), action.getResultDetail(), action.getOccurredAt());
    }

    @Override
    public List<CalculationRetryAction> findByJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) throw new IllegalArgumentException("jobId 不能为空");
        return jdbc.query("SELECT action_id, job_id, request_key, action_type, operator, reason, "
                        + "original_attempt_no, result, result_detail, occurred_at "
                        + "FROM flow.calculation_retry_action WHERE job_id = ? ORDER BY action_id",
                (rs, rowNum) -> map(rs), jobId.trim());
    }

    private CalculationRetryAction map(ResultSet rs) throws SQLException {
        return new CalculationRetryAction(rs.getLong("action_id"), rs.getString("job_id"),
                rs.getString("request_key"), CalculationRetryAction.Type.valueOf(rs.getString("action_type")),
                rs.getString("operator"), rs.getString("reason"), rs.getInt("original_attempt_no"),
                rs.getString("result"), rs.getString("result_detail"),
                rs.getTimestamp("occurred_at").toLocalDateTime());
    }
}
