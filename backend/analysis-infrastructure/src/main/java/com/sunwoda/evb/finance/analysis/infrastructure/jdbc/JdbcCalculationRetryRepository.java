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
import java.util.UUID;

/**
 * 计算失败处理状态 JDBC 仓储。
 *
 * <p>本次新增：只持久化 V028 状态表，不改动 V026 Outbox 和既有计算服务。</p>
 * <p>本次补充：db profile 下标记为首选实现，与既有内存仓储并存时保证数据库接线明确。</p>
 * <p>本次修复：到期查询通过 V029 领取表和 PostgreSQL 行锁/CAS 原子登记 Worker 租约，避免多个 Worker
 * 同时看到同一条 PENDING_RETRY 记录。</p>
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
    private static final String CLAIM_DUE_SQL =
            "WITH due AS ("
                    + "SELECT r.job_id FROM flow.calculation_retry r "
                    + "LEFT JOIN flow.calculation_retry_claim c ON c.job_id = r.job_id "
                    + "AND c.claimed_until > ? "
                    + "WHERE r.status = 'PENDING_RETRY' AND r.next_retry_at <= ? "
                    + "AND c.job_id IS NULL "
                    + "ORDER BY r.next_retry_at, r.job_id LIMIT ? "
                    + "FOR UPDATE OF r SKIP LOCKED), "
                    + "claimed AS ("
                    + "INSERT INTO flow.calculation_retry_claim "
                    + "(job_id, worker_id, claimed_until, created_at, updated_at) "
                    + "SELECT job_id, ?, ?, ?, ? FROM due "
                    + "ON CONFLICT (job_id) DO UPDATE SET worker_id = EXCLUDED.worker_id, "
                    + "claimed_until = EXCLUDED.claimed_until, updated_at = EXCLUDED.updated_at "
                    + "WHERE flow.calculation_retry_claim.claimed_until <= ? "
                    + "RETURNING job_id) "
                    + "SELECT r.job_id, r.request_key, r.status, r.retry_no, r.last_failed_attempt_no, "
                    + "r.next_retry_at, r.last_error, r.created_at, r.updated_at "
                    + "FROM flow.calculation_retry r JOIN claimed c ON c.job_id = r.job_id "
                    + "ORDER BY r.next_retry_at, r.job_id";
    private static final long CLAIM_LEASE_MINUTES = 5L;
    private final JdbcTemplate jdbc;
    private final String workerId;

    public JdbcCalculationRetryRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource 不能为空");
        this.jdbc = new JdbcTemplate(dataSource);
        // 本次修复：每个仓储实例使用独立 Worker 标识，便于排查租约持有者并支持并发领取测试。
        this.workerId = "analysis-worker-" + UUID.randomUUID().toString();
    }

    @Override
    public CalculationRetryState save(CalculationRetryState state) {
        if (state == null) throw new IllegalArgumentException("state 不能为空");
        // 本次新增：状态只按 job_id 幂等更新，request_key 的唯一性由 V028 数据库约束兜底。
        jdbc.update(UPSERT_SQL, state.getJobId(), state.getRequestKey(), state.getStatus().name(),
                state.getRetryNo(), state.getLastFailedAttemptNo(), timestamp(state.getNextRetryAt()),
                state.getLastError(), timestamp(state.getCreatedAt()), timestamp(state.getUpdatedAt()));
        if (state.getStatus() != CalculationRetryStatus.PENDING_RETRY) {
            // 本次修复：状态离开 PENDING_RETRY 后立即释放领取租约，避免下一次失败被旧租约延迟。
            jdbc.update("DELETE FROM flow.calculation_retry_claim WHERE job_id = ?", state.getJobId());
        }
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
        LocalDateTime claimedUntil = now.plusMinutes(CLAIM_LEASE_MINUTES);
        // 本次修复：单条 SQL 内先 SKIP LOCKED 锁定到期行，再以唯一 job_id 原子登记领取租约。
        return jdbc.query(CLAIM_DUE_SQL, (rs, rowNum) -> map(rs),
                timestamp(now), timestamp(now), limit, workerId, timestamp(claimedUntil),
                timestamp(now), timestamp(now), timestamp(now));
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
