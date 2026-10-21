package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
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
 * 计算失败处理状态 JDBC 仓储。
 *
 * <p>本次新增：只持久化 V028 状态表，不改动 V026 Outbox 和既有计算服务。</p>
 * <p>本次补充：db profile 下标记为首选实现，与既有内存仓储并存时保证数据库接线明确。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationRetryRepository implements CalculationRetryRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO flow.calculation_retry "
                    + "(job_id, request_key, status, retry_no, last_failed_attempt_no, next_retry_at, "
                    + "last_error, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (job_id) DO UPDATE SET request_key = EXCLUDED.request_key, "
                    + "status = EXCLUDED.status, retry_no = EXCLUDED.retry_no, "
                    + "last_failed_attempt_no = EXCLUDED.last_failed_attempt_no, "
                    + "next_retry_at = EXCLUDED.next_retry_at, last_error = EXCLUDED.last_error, "
                    + "created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at";
    private static final String SELECT_COLUMNS =
            "job_id, request_key, status, retry_no, last_failed_attempt_no, next_retry_at, "
                    + "last_error, created_at, updated_at";
    private final JdbcTemplate jdbc;

    public JdbcCalculationRetryRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource 不能为空");
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public CalculationRetryState save(CalculationRetryState state) {
        if (state == null) throw new IllegalArgumentException("state 不能为空");
        // 本次新增：状态只按 job_id 幂等更新，request_key 的唯一性由 V028 数据库约束兜底。
        jdbc.update(UPSERT_SQL, state.getJobId(), state.getRequestKey(), state.getStatus().name(),
                state.getRetryNo(), state.getLastFailedAttemptNo(), timestamp(state.getNextRetryAt()),
                state.getLastError(), timestamp(state.getCreatedAt()), timestamp(state.getUpdatedAt()));
        return state;
    }

    @Override
    public Optional<CalculationRetryState> findByJobId(String jobId) {
        return queryOne("SELECT " + SELECT_COLUMNS + " FROM flow.calculation_retry WHERE job_id = ?", jobId);
    }

    @Override
    public Optional<CalculationRetryState> findByRequestKey(String requestKey) {
        return queryOne("SELECT " + SELECT_COLUMNS + " FROM flow.calculation_retry WHERE request_key = ?", requestKey);
    }

    @Override
    public List<CalculationRetryState> findDue(LocalDateTime now, int limit) {
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        return jdbc.query("SELECT " + SELECT_COLUMNS + " FROM flow.calculation_retry "
                        + "WHERE status = 'PENDING_RETRY' AND next_retry_at <= ? "
                        + "ORDER BY next_retry_at, job_id LIMIT ?",
                (rs, rowNum) -> map(rs), timestamp(now), limit);
    }

    private Optional<CalculationRetryState> queryOne(String sql, String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("查询值不能为空");
        return jdbc.query(sql, rs -> rs.next() ? Optional.of(map(rs)) : Optional.<CalculationRetryState>empty(), value);
    }

    private CalculationRetryState map(ResultSet rs) throws SQLException {
        return CalculationRetryState.restore(rs.getString("job_id"), rs.getString("request_key"),
                CalculationRetryStatus.valueOf(rs.getString("status")), rs.getInt("retry_no"),
                (Integer) rs.getObject("last_failed_attempt_no"), localDateTime(rs.getTimestamp("next_retry_at")),
                rs.getString("last_error"), localDateTime(rs.getTimestamp("created_at")),
                localDateTime(rs.getTimestamp("updated_at")));
    }

    private Timestamp timestamp(LocalDateTime value) { return value == null ? null : Timestamp.valueOf(value); }
    private LocalDateTime localDateTime(Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
}
