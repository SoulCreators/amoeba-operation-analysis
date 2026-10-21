package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryClaim;
import com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL Outbox 投递协调适配器。
 *
 * <p>本次新增：通过 V032 协调表保存租约和令牌，使用 SKIP LOCKED 原子领取，
 * 使用 event_id + claim_token + 租约时间 CAS 确认成功或失败；不改变既有 CalculationLifecycleEventRepository。</p>
 */
@Repository
@Profile("db")
public class JdbcCalculationLifecycleEventDeliveryRepository
        implements CalculationLifecycleEventDeliveryPort {
    private static final String CLAIM_SQL =
            "WITH due AS ("
                    + "SELECT o.event_id FROM flow.calculation_event_outbox o "
                    + "LEFT JOIN flow.calculation_event_outbox_claim c ON c.event_id = o.event_id "
                    + "WHERE ((o.delivery_status IN ('PENDING', 'FAILED') AND o.next_attempt_at <= ?) "
                    + "OR (o.delivery_status = 'IN_PROGRESS' "
                    + "AND (c.event_id IS NULL OR c.claimed_until <= ?))) "
                    + "AND (c.event_id IS NULL OR c.claimed_until <= ?) "
                    + "ORDER BY o.next_attempt_at, o.id LIMIT ? FOR UPDATE OF o SKIP LOCKED), "
                    + "claimed AS ("
                    + "INSERT INTO flow.calculation_event_outbox_claim "
                    + "(event_id, worker_id, claim_token, claimed_until, created_at, updated_at) "
                    + "SELECT d.event_id, ?, md5(random()::text || clock_timestamp()::text || d.event_id), ?, ?, ? FROM due d "
                    + "ON CONFLICT (event_id) DO UPDATE SET worker_id = EXCLUDED.worker_id, "
                    + "claim_token = EXCLUDED.claim_token, claimed_until = EXCLUDED.claimed_until, "
                    + "updated_at = EXCLUDED.updated_at "
                    + "WHERE flow.calculation_event_outbox_claim.claimed_until <= ? "
                    + "RETURNING event_id, worker_id, claim_token, claimed_until), "
                    + "marked AS ("
                    + "UPDATE flow.calculation_event_outbox o SET delivery_status = 'IN_PROGRESS', "
                    + "attempt_no = o.attempt_no + 1 "
                    + "FROM claimed c WHERE c.event_id = o.event_id "
                    + "RETURNING o.event_id, o.aggregate_type, o.aggregate_id, o.event_type, "
                    + "o.calculation_version_id, o.trace_id, o.payload_json, o.delivery_status, "
                    + "o.attempt_no, o.next_attempt_at, o.last_error, o.occurred_at, o.delivered_at, "
                    + "c.worker_id, c.claim_token, c.claimed_until) "
                    + "SELECT * FROM marked ORDER BY next_attempt_at, event_id";
    private static final String EVENT_COLUMNS =
            "event_id, aggregate_type, aggregate_id, event_type, calculation_version_id, trace_id, "
                    + "payload_json, delivery_status, attempt_no, next_attempt_at, last_error, occurred_at, "
                    + "delivered_at";

    private final JdbcTemplate jdbc;
    private final String workerId;

    public JdbcCalculationLifecycleEventDeliveryRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        // 本次新增：每个适配器实例使用独立 Worker 标识，便于审计租约持有者和并发故障排查。
        this.workerId = "outbox-worker-" + UUID.randomUUID().toString();
    }

    @Override
    public List<CalculationLifecycleEventDeliveryClaim> claimReadyForDelivery(
            LocalDateTime now, int limit, Duration leaseDuration) {
        return claimReadyForDelivery(workerId, now, limit, leaseDuration);
    }

    @Override
    public List<CalculationLifecycleEventDeliveryClaim> claimReadyForDelivery(
            String requestedWorkerId, LocalDateTime now, int limit, Duration leaseDuration) {
        validateClaimArguments(now, limit, leaseDuration);
        String currentWorkerId = required(requestedWorkerId, "workerId");
        LocalDateTime claimedUntil = now.plus(leaseDuration);
        // 本次新增：一条 CTE 同时完成 SKIP LOCKED、租约写入和 IN_PROGRESS 状态更新，避免重复领取。
        return jdbc.query(CLAIM_SQL, (rs, rowNum) -> mapClaim(rs),
                timestamp(now), timestamp(now), timestamp(now), limit,
                currentWorkerId, timestamp(claimedUntil),
                timestamp(now), timestamp(now), timestamp(now));
    }

    @Override
    @Transactional
    public boolean markDelivered(String eventId, String claimToken, LocalDateTime deliveredAt) {
        String id = required(eventId, "eventId");
        String token = required(claimToken, "claimToken");
        if (deliveredAt == null) {
            throw new IllegalArgumentException("deliveredAt 不能为空");
        }
        // 本次新增：成功确认必须匹配活动租约和令牌；过期/被接管的旧 Worker 只能得到 false。
        int updated = jdbc.update("UPDATE flow.calculation_event_outbox o SET delivery_status = 'DELIVERED', "
                        + "delivered_at = ?, next_attempt_at = ?, last_error = NULL "
                        + "WHERE o.event_id = ? AND o.delivery_status = 'IN_PROGRESS' "
                        + "AND EXISTS (SELECT 1 FROM flow.calculation_event_outbox_claim c "
                        + "WHERE c.event_id = o.event_id AND c.claim_token = ? "
                        + "AND c.claimed_until > CURRENT_TIMESTAMP)",
                timestamp(deliveredAt), timestamp(deliveredAt), id, token);
        if (updated != 1) {
            return false;
        }
        // 本次新增：确认后清理租约，避免历史令牌影响后续人工查询和再次投递。
        jdbc.update("DELETE FROM flow.calculation_event_outbox_claim WHERE event_id = ? AND claim_token = ?",
                id, token);
        return true;
    }

    @Override
    @Transactional
    public boolean markFailed(String eventId, String claimToken, CalculationEventDeliveryStatus failureStatus,
                              String lastError, LocalDateTime nextAttemptAt) {
        String id = required(eventId, "eventId");
        String token = required(claimToken, "claimToken");
        if (failureStatus != CalculationEventDeliveryStatus.FAILED
                && failureStatus != CalculationEventDeliveryStatus.MANUAL_REQUIRED) {
            throw new IllegalArgumentException("failureStatus 只能是 FAILED 或 MANUAL_REQUIRED");
        }
        String error = required(lastError, "lastError");
        if (nextAttemptAt == null) {
            throw new IllegalArgumentException("nextAttemptAt 不能为空");
        }
        // 本次新增：失败状态更新同样使用租约令牌 CAS，MANUAL_REQUIRED 不再进入自动领取条件。
        int updated = jdbc.update("UPDATE flow.calculation_event_outbox o SET delivery_status = ?, "
                        + "last_error = ?, next_attempt_at = ?, delivered_at = NULL "
                        + "WHERE o.event_id = ? AND o.delivery_status = 'IN_PROGRESS' "
                        + "AND EXISTS (SELECT 1 FROM flow.calculation_event_outbox_claim c "
                        + "WHERE c.event_id = o.event_id AND c.claim_token = ? "
                        + "AND c.claimed_until > CURRENT_TIMESTAMP)",
                failureStatus.name(), error, timestamp(nextAttemptAt), id, token);
        if (updated != 1) {
            return false;
        }
        // 本次新增：失败/人工接管确认后释放租约，后续 FAILED 由 next_attempt_at 控制，人工状态保持终态。
        jdbc.update("DELETE FROM flow.calculation_event_outbox_claim WHERE event_id = ? AND claim_token = ?",
                id, token);
        return true;
    }

    private CalculationLifecycleEventDeliveryClaim mapClaim(ResultSet rs) throws SQLException {
        CalculationLifecycleEvent event = new CalculationLifecycleEvent(
                rs.getString("event_id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("calculation_version_id"),
                rs.getString("trace_id"),
                rs.getString("payload_json"),
                CalculationEventDeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getInt("attempt_no"),
                localDateTime(rs.getTimestamp("next_attempt_at")),
                rs.getString("last_error"),
                localDateTime(rs.getTimestamp("occurred_at")),
                localDateTime(rs.getTimestamp("delivered_at")));
        return new CalculationLifecycleEventDeliveryClaim(event, rs.getString("worker_id"),
                rs.getString("claim_token"), localDateTime(rs.getTimestamp("claimed_until")));
    }

    private void validateClaimArguments(LocalDateTime now, int limit, Duration leaseDuration) {
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration 必须大于 0");
        }
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
