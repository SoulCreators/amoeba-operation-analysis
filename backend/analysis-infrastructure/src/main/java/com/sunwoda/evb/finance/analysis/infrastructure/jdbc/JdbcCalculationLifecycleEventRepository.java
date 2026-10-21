package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationLifecycleEventRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * V026 计算生命周期 Outbox 的 PostgreSQL JDBC 仓储。
 *
 * <p>本次新增：复用 flow.calculation_event_outbox 的 JSONB、投递状态、重试次数和时间字段，
 * 支持同事务追加、按 event_id 查询以及按状态/到期时间读取待投递事件。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationLifecycleEventRepository implements CalculationLifecycleEventRepository {
    private static final String EVENT_COLUMNS =
            "event_id, aggregate_type, aggregate_id, event_type, calculation_version_id, trace_id, "
                    + "payload_json, delivery_status, attempt_no, next_attempt_at, last_error, occurred_at, "
                    + "delivered_at";

    private final JdbcTemplate jdbc;

    public JdbcCalculationLifecycleEventRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 本次新增：按 event_id 幂等保存 Outbox 全部字段，JSON 正文交由 PostgreSQL JSONB 约束校验；
     * 事件唯一键和版本外键继续复用 V026 数据库约束。
     */
    @Override
    @Transactional
    public CalculationLifecycleEvent save(CalculationLifecycleEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event 不能为空");
        }
        jdbc.update("INSERT INTO flow.calculation_event_outbox ("
                        + "event_id, aggregate_type, aggregate_id, event_type, calculation_version_id, trace_id, "
                        + "payload_json, delivery_status, attempt_no, next_attempt_at, last_error, occurred_at, "
                        + "delivered_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (event_id) DO UPDATE SET aggregate_type = EXCLUDED.aggregate_type, "
                        + "aggregate_id = EXCLUDED.aggregate_id, event_type = EXCLUDED.event_type, "
                        + "calculation_version_id = EXCLUDED.calculation_version_id, trace_id = EXCLUDED.trace_id, "
                        + "payload_json = EXCLUDED.payload_json, delivery_status = EXCLUDED.delivery_status, "
                        + "attempt_no = EXCLUDED.attempt_no, next_attempt_at = EXCLUDED.next_attempt_at, "
                        + "last_error = EXCLUDED.last_error, occurred_at = EXCLUDED.occurred_at, "
                        + "delivered_at = EXCLUDED.delivered_at",
                event.getEventId(), event.getAggregateType(), event.getAggregateId(), event.getEventType(),
                event.getCalculationVersionId(), event.getTraceId(), event.getPayloadJson(),
                event.getDeliveryStatus().name(), event.getAttemptNo(), timestamp(event.getNextAttemptAt()),
                event.getLastError(), timestamp(event.getOccurredAt()), timestamp(event.getDeliveredAt()));
        return event;
    }

    @Override
    public Optional<CalculationLifecycleEvent> findByEventId(String eventId) {
        String id = required(eventId, "eventId");
        return jdbc.query("SELECT " + EVENT_COLUMNS + " FROM flow.calculation_event_outbox "
                        + "WHERE event_id = ?", this::one, id);
    }

    @Override
    public List<CalculationLifecycleEvent> findReadyForDelivery(CalculationEventDeliveryStatus status,
                                                                LocalDateTime now, int limit) {
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        return jdbc.query("SELECT " + EVENT_COLUMNS + " FROM flow.calculation_event_outbox "
                        + "WHERE delivery_status = ? AND next_attempt_at <= ? "
                        + "ORDER BY next_attempt_at, id LIMIT ?", (rs, rowNum) -> map(rs),
                status.name(), timestamp(now), limit);
    }

    private Optional<CalculationLifecycleEvent> one(ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return Optional.empty();
        }
        return Optional.of(map(rs));
    }

    private CalculationLifecycleEvent map(ResultSet rs) throws SQLException {
        return new CalculationLifecycleEvent(
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
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
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
