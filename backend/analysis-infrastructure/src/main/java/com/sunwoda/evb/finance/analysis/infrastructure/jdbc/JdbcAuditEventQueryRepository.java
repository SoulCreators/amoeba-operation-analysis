package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventQueryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL 审计事件只读查询适配器。
 *
 * <p>本次新增：只实现现有 application List 查询端口，固定投影、精确筛选、半开时间区间和确定性排序；
 * application 端口没有 cursor/pageSize，因此分页留作后续边界。</p>
 */
@Repository
@Profile("db")
public class JdbcAuditEventQueryRepository implements AuditEventQueryPort {
    private static final String SELECT_COLUMNS =
            "event_id, trace_id, operator, service, action, object_type, object_id, "
                    + "from_state, to_state, result, reason, metadata, occurred_at";

    private final JdbcTemplate jdbc;

    // 本次新增：显式使用 DataSource 构造查询适配器，保持与既有 JDBC append 适配器的接线方式一致。
    @Autowired
    public JdbcAuditEventQueryRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // 本次新增：保留包可见 JdbcTemplate 构造函数，供 SQL/映射契约测试隔离真实数据库。
    JdbcAuditEventQueryRepository(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public List<AuditEvent> find(AuditEventQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query 不能为空");
        }

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(SELECT_COLUMNS)
                .append(" FROM audit.audit_event WHERE 1 = 1");
        List<Object> arguments = new ArrayList<Object>();

        // 本次新增：所有文本条件均按完整字段值精确匹配，不把查询条件扩展为模糊或 metadata 过滤。
        appendEquals(sql, arguments, "event_id", query.getEventId());
        appendEquals(sql, arguments, "trace_id", query.getTraceId());
        appendEquals(sql, arguments, "operator", query.getOperator());
        appendEquals(sql, arguments, "service", query.getService());
        appendEquals(sql, arguments, "action", query.getAction());
        appendEquals(sql, arguments, "object_type", query.getObjectType());
        appendEquals(sql, arguments, "object_id", query.getObjectId());
        appendEquals(sql, arguments, "result", query.getResult());

        // 本次新增：时间范围采用 [occurredFrom, occurredTo)，两端可分别缺省且上界始终排除。
        if (query.getOccurredFrom() != null) {
            sql.append(" AND occurred_at >= ?");
            arguments.add(timestamp(query.getOccurredFrom()));
        }
        if (query.getOccurredTo() != null) {
            sql.append(" AND occurred_at < ?");
            arguments.add(timestamp(query.getOccurredTo()));
        }

        // 本次新增：固定时间倒序并以 event_id 倒序打破同一时间点的并列，分页暂不在当前端口表达。
        sql.append(" ORDER BY occurred_at DESC, event_id DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> map(rs), arguments.toArray());
    }

    /** 本次新增：只为非空条件追加等值谓词，避免动态列名和未约束的 SQL 片段进入查询。 */
    private static void appendEquals(StringBuilder sql, List<Object> arguments,
                                     String column, String value) {
        if (value == null) return;
        sql.append(" AND ").append(column).append(" = ?");
        arguments.add(value);
    }

    /** 本次新增：将数据库固定投影按 AuditEvent 既有字段顺序还原，不读取未声明列。 */
    private static AuditEvent map(ResultSet rs) throws SQLException {
        Timestamp occurredAt = rs.getTimestamp("occurred_at");
        return new AuditEvent(rs.getString("event_id"), rs.getString("trace_id"),
                rs.getString("operator"), rs.getString("service"), rs.getString("action"),
                rs.getString("object_type"), rs.getString("object_id"), rs.getString("from_state"),
                rs.getString("to_state"), rs.getString("result"), rs.getString("reason"),
                rs.getString("metadata"), occurredAt == null ? null : occurredAt.toLocalDateTime());
    }

    /** 本次新增：沿用 PostgreSQL timestamp 无时区 schema 的 JDBC 转换方式。 */
    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
