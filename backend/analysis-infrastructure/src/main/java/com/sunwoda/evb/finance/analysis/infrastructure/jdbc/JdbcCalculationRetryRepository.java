package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaimLostException;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * 计算失败处理状态 JDBC 仓储。
 *
 * <p>本次新增：只持久化 V028 状态表，不改动 V026 Outbox 和既有计算服务。</p>
 * <p>本次补充：db profile 下标记为首选实现，与既有内存仓储并存时保证数据库接线明确。</p>
 * <p>本次修复：到期查询通过 V029/V034 领取表、claim token 和 PostgreSQL 行锁/CAS 原子登记 Worker 租约，
 * 避免多个 Worker 同时看到同一条 PENDING_RETRY 记录，并拒绝旧租约继续写入。</p>
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
                    + "(job_id, worker_id, claim_token, claimed_until, created_at, updated_at) "
                    + "SELECT job_id, ?, md5(job_id || ? || clock_timestamp()::text || random()::text), ?, ?, ? FROM due "
                    + "ON CONFLICT (job_id) DO UPDATE SET worker_id = EXCLUDED.worker_id, "
                    + "claimed_until = EXCLUDED.claimed_until, updated_at = EXCLUDED.updated_at "
                    + "WHERE flow.calculation_retry_claim.claimed_until <= ? "
                    + "RETURNING job_id, claim_token, claimed_until) "
                    + "SELECT r.job_id, r.request_key, r.status, r.retry_no, r.last_failed_attempt_no, "
                    + "r.next_retry_at, r.last_error, r.created_at, r.updated_at, c.claim_token, c.claimed_until "
                    + "FROM flow.calculation_retry r JOIN claimed c ON c.job_id = r.job_id "
                    + "ORDER BY r.next_retry_at, r.job_id";
    private static final String LOCK_CLAIM_SQL =
            "SELECT c.job_id FROM flow.calculation_retry_claim c "
                    + "JOIN flow.calculation_retry r ON r.job_id = c.job_id "
                    + "WHERE c.job_id = ? AND c.claim_token = ? AND c.claimed_until > ? "
                    + "AND r.status = 'PENDING_RETRY' FOR UPDATE OF c, r";
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
        List<CalculationRetryClaim> claims = findDueClaims(now, limit);
        List<CalculationRetryState> states = new ArrayList<CalculationRetryState>();
        for (CalculationRetryClaim claim : claims) states.add(claim.getState());
        return states;
    }

    @Override
    public List<CalculationRetryClaim> findDueClaims(LocalDateTime now, int limit) {
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        LocalDateTime claimedUntil = now.plusMinutes(CLAIM_LEASE_MINUTES);
        // 本次修复：单条 SQL 内生成不可伪造的 token，并把 token 随领取结果返回给执行事务。
        return jdbc.query(CLAIM_DUE_SQL, (rs, rowNum) -> new CalculationRetryClaim(
                        map(rs), rs.getString("claim_token"), localDateTime(rs.getTimestamp("claimed_until"))),
                timestamp(now), timestamp(now), limit, workerId, workerId, timestamp(claimedUntil),
                timestamp(now), timestamp(now), timestamp(now));
    }

    @Override
    public void lockClaim(String jobId, String claimToken, LocalDateTime now) {
        if (jobId == null || jobId.trim().isEmpty()) throw new IllegalArgumentException("jobId 不能为空");
        if (claimToken == null || claimToken.trim().isEmpty()) {
            throw new IllegalArgumentException("claimToken 不能为空");
        }
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        Boolean owned = jdbc.query(LOCK_CLAIM_SQL,
                (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(),
                jobId, claimToken, timestamp(now));
        if (!Boolean.TRUE.equals(owned)) {
            throw new CalculationRetryClaimLostException("计算重试租约已失效: jobId=" + jobId);
        }
    }

    @Override
    public void releaseClaim(String jobId, String claimToken) {
        if (jobId == null || jobId.trim().isEmpty()) throw new IllegalArgumentException("jobId 不能为空");
        if (claimToken == null || claimToken.trim().isEmpty()) {
            throw new IllegalArgumentException("claimToken 不能为空");
        }
        // 本次新增：按 job_id + token 双条件删除，旧 Worker 只能删除自己的租约。
        jdbc.update("DELETE FROM flow.calculation_retry_claim WHERE job_id = ? AND claim_token = ?",
                jobId, claimToken);
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
