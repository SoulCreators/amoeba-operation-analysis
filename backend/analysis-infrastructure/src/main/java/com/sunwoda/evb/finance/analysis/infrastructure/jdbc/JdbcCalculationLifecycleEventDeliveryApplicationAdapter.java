package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventDeliveryTask;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventDeliveryPort;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryClaim;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 应用端口的 PostgreSQL 适配器。
 *
 * <p>本次修复：把基础设施原子领取端口桥接到 application port，避免 Worker 因同名不同包接口
 * 没有生产接线；人工重试/补偿使用现有追加式审计表记录，不伪造外部投递成功。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationLifecycleEventDeliveryApplicationAdapter
        implements CalculationLifecycleEventDeliveryPort {
    private final JdbcTemplate jdbc;
    private final com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryPort delegate;

    public JdbcCalculationLifecycleEventDeliveryApplicationAdapter(
            DataSource dataSource,
            com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryPort delegate) {
        if (dataSource == null || delegate == null) {
            throw new IllegalArgumentException("Outbox 适配器依赖不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.delegate = delegate;
    }

    @Override
    public List<CalculationEventDeliveryTask> claimReady(String workerId, int limit,
                                                          LocalDateTime now,
                                                          LocalDateTime leaseUntil) {
        if (workerId == null || workerId.trim().isEmpty()) {
            throw new IllegalArgumentException("workerId 不能为空");
        }
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("租约时间必须有效");
        }
        List<CalculationLifecycleEventDeliveryClaim> claims = delegate.claimReadyForDelivery(
                workerId.trim(), now, limit, Duration.between(now, leaseUntil));
        java.util.ArrayList<CalculationEventDeliveryTask> tasks =
                new java.util.ArrayList<CalculationEventDeliveryTask>();
        for (CalculationLifecycleEventDeliveryClaim claim : claims) {
            tasks.add(new CalculationEventDeliveryTask(claim.getEvent(), claim.getClaimToken(),
                    now, claim.getClaimedUntil()));
        }
        return tasks;
    }

    @Override
    public boolean markDelivered(String eventId, String claimToken, LocalDateTime deliveredAt) {
        return delegate.markDelivered(eventId, claimToken, deliveredAt);
    }

    @Override
    public boolean markFailed(String eventId, String claimToken, String errorMessage,
                              LocalDateTime nextAttemptAt, LocalDateTime failedAt, boolean terminal) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt 不能为空");
        }
        return delegate.markFailed(eventId, claimToken,
                terminal ? CalculationEventDeliveryStatus.MANUAL_REQUIRED
                        : CalculationEventDeliveryStatus.FAILED,
                errorMessage, nextAttemptAt);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void manualRetry(String eventId, String operator, String reason, LocalDateTime operatedAt) {
        ManualEvent event = requireManualEvent(eventId);
        int updated = jdbc.update("UPDATE flow.calculation_event_outbox SET delivery_status = 'PENDING', "
                        + "next_attempt_at = ?, delivered_at = NULL "
                        + "WHERE event_id = ? AND delivery_status = 'MANUAL_REQUIRED'",
                Timestamp.valueOf(operatedAt), eventId);
        if (updated != 1) {
            throw new IllegalStateException("Outbox 事件不在 MANUAL_REQUIRED: " + eventId);
        }
        appendAudit(event, operator, "OUTBOX_MANUAL_RETRY", "PENDING", reason, operatedAt);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void manualCompensate(String eventId, String operator, String reason, LocalDateTime operatedAt) {
        ManualEvent event = requireManualEvent(eventId);
        // 本次新增：人工补偿只追加审计，不把 MANUAL_REQUIRED 伪造为 DELIVERED。
        appendAudit(event, operator, "OUTBOX_MANUAL_COMPENSATE", "MANUAL_REQUIRED", reason, operatedAt);
    }

    private ManualEvent requireManualEvent(String eventId) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId 不能为空");
        }
        return jdbc.query("SELECT event_id, trace_id, delivery_status FROM flow.calculation_event_outbox "
                        + "WHERE event_id = ?", rs -> {
                    if (!rs.next()) throw new IllegalArgumentException("Outbox 事件不存在: " + eventId);
                    if (!"MANUAL_REQUIRED".equals(rs.getString("delivery_status"))) {
                        throw new IllegalStateException("Outbox 事件不在 MANUAL_REQUIRED: " + eventId);
                    }
                    return new ManualEvent(rs.getString("event_id"), rs.getString("trace_id"));
                }, eventId.trim());
    }

    private void appendAudit(ManualEvent event, String operator, String action, String toState,
                             String reason, LocalDateTime operatedAt) {
        if (operator == null || operator.trim().isEmpty() || reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("人工操作员和原因不能为空");
        }
        jdbc.update("INSERT INTO audit.audit_event (event_id, trace_id, operator, service, action, "
                        + "object_type, object_id, from_state, to_state, result, reason, metadata, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                "OUTBOX-AUDIT-" + UUID.randomUUID(), event.traceId, operator.trim(),
                "analysis-outbox", action, "CALCULATION_EVENT_OUTBOX", event.eventId,
                "MANUAL_REQUIRED", toState, "RECORDED", reason.trim(), "{}", Timestamp.valueOf(operatedAt));
    }

    private static final class ManualEvent {
        private final String eventId;
        private final String traceId;

        private ManualEvent(String eventId, String traceId) {
            this.eventId = eventId;
            this.traceId = traceId;
        }
    }
}
