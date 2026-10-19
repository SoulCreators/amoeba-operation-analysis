package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;

/** PostgreSQL adapter for the independent audit event contract. */
@Repository
@Profile("db")
public class JdbcAuditEventRepository implements AuditEventPort {
    private static final String INSERT_SQL =
            "INSERT INTO audit.audit_event ("
                    + "event_id, trace_id, operator, service, action, object_type, object_id, "
                    + "from_state, to_state, result, reason, metadata, occurred_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)";

    private final JdbcTemplate jdbc;

    public JdbcAuditEventRepository(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    JdbcAuditEventRepository(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event 不能为空");
        }
        jdbc.update(INSERT_SQL, event.getEventId(), event.getTraceId(), event.getOperator(),
                event.getService(), event.getAction(), event.getObjectType(), event.getObjectId(),
                event.getFromState(), event.getToState(), event.getResult(), event.getReason(),
                event.getMetadata(), Timestamp.valueOf(event.getOccurredAt()));
    }
}
