package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * JdbcAuditEventQueryRepository 离线查询契约测试。
 *
 * <p>本次新增：不依赖数据库，锁定固定投影、精确谓词、半开时间边界、排序和字段映射。</p>
 */
class JdbcAuditEventQueryRepositoryTest {
    @Test
    void buildsFixedProjectionExactFiltersHalfOpenRangeAndDeterministicOrder() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(Collections.<AuditEvent>emptyList());
        CapturedQuery captured = jdbc.captured;
        JdbcAuditEventQueryRepository repository = new JdbcAuditEventQueryRepository(jdbc);
        LocalDateTime from = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 1, 0, 0);

        repository.find(new AuditEventQuery(" EVT-001 ", "TRACE-001", "operator",
                "analysis-api", "UPDATE", "export_task", "EXP-001", "SUCCESS", from, to));

        // 本次新增：固定列清单必须覆盖 AuditEvent 全部字段，且查询实现不得退化为 SELECT *。
        assertTrue(captured.sql.startsWith("SELECT event_id, trace_id, operator, service, action, object_type, object_id, "
                + "from_state, to_state, result, reason, metadata, occurred_at FROM audit.audit_event"));
        assertFalse(captured.sql.matches("(?is).*\\bSELECT\\s+\\*\\b.*"));
        assertTrue(captured.sql.contains("event_id = ?"));
        assertTrue(captured.sql.contains("occurred_at >= ?"));
        assertTrue(captured.sql.contains("occurred_at < ?"));
        assertTrue(captured.sql.endsWith("ORDER BY occurred_at DESC, event_id DESC"));
        assertEquals(Arrays.asList("EVT-001", "TRACE-001", "operator", "analysis-api", "UPDATE",
                "export_task", "EXP-001", "SUCCESS", Timestamp.valueOf(from), Timestamp.valueOf(to)),
                Arrays.asList(captured.arguments));
    }

    @Test
    void mapsAllProjectedFieldsAndRejectsNullQuery() throws Exception {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(Collections.<AuditEvent>emptyList());
        CapturedQuery captured = jdbc.captured;
        JdbcAuditEventQueryRepository repository = new JdbcAuditEventQueryRepository(jdbc);
        ResultSet rs = mock(ResultSet.class);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 21, 12, 34, 56);
        org.mockito.Mockito.when(rs.getString("event_id")).thenReturn("EVT-001");
        org.mockito.Mockito.when(rs.getString("trace_id")).thenReturn("TRACE-001");
        org.mockito.Mockito.when(rs.getString("operator")).thenReturn("operator");
        org.mockito.Mockito.when(rs.getString("service")).thenReturn("analysis-api");
        org.mockito.Mockito.when(rs.getString("action")).thenReturn("UPDATE");
        org.mockito.Mockito.when(rs.getString("object_type")).thenReturn("export_task");
        org.mockito.Mockito.when(rs.getString("object_id")).thenReturn("EXP-001");
        org.mockito.Mockito.when(rs.getString("from_state")).thenReturn("QUEUED");
        org.mockito.Mockito.when(rs.getString("to_state")).thenReturn("SUCCEEDED");
        org.mockito.Mockito.when(rs.getString("result")).thenReturn("SUCCESS");
        org.mockito.Mockito.when(rs.getString("metadata")).thenReturn("{\"source\":\"test\"}");
        org.mockito.Mockito.when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.valueOf(occurredAt));

        repository.find(AuditEventQuery.all());
        AuditEvent mapped = captured.rowMapper.mapRow(rs, 0);

        // 本次新增：离线映射契约锁定已有字段，查询适配器只读而不调用既有追加仓储。
        assertEquals("EVT-001", mapped.getEventId());
        assertEquals("TRACE-001", mapped.getTraceId());
        assertEquals("operator", mapped.getOperator());
        assertEquals("analysis-api", mapped.getService());
        assertEquals("UPDATE", mapped.getAction());
        assertEquals("export_task", mapped.getObjectType());
        assertEquals("EXP-001", mapped.getObjectId());
        assertEquals("QUEUED", mapped.getFromState());
        assertEquals("SUCCEEDED", mapped.getToState());
        assertEquals("SUCCESS", mapped.getResult());
        assertEquals("{\"source\":\"test\"}", mapped.getMetadata());
        assertEquals(occurredAt, mapped.getOccurredAt());
        assertFalse(jdbc.updateCalled);
        assertThrows(IllegalArgumentException.class, () -> repository.find(null));
    }

    @Test
    void omitsAbsentFilters() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(Collections.<AuditEvent>emptyList());
        CapturedQuery captured = jdbc.captured;
        new JdbcAuditEventQueryRepository(jdbc).find(AuditEventQuery.all());
        // 本次新增：空条件仍使用固定投影和排序；cursor/pageSize 由后续端口增量处理。
        assertFalse(captured.sql.contains(" = ?"));
        assertTrue(captured.sql.contains("WHERE 1 = 1"));
        assertEquals(0, captured.arguments.length);
    }

    private static final class CapturedQuery {
        private String sql;
        private Object[] arguments = new Object[0];
        private RowMapper<AuditEvent> rowMapper;
    }

    /** 本次修复：用可记录的 JdbcTemplate 替代可变参数重载 Mock，避免不同 Mockito 版本的重载匹配差异。 */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final CapturedQuery captured = new CapturedQuery();
        private final List<AuditEvent> result;
        private boolean updateCalled;

        private RecordingJdbcTemplate(List<AuditEvent> result) {
            this.result = result;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            captured.sql = sql;
            captured.rowMapper = (RowMapper<AuditEvent>) rowMapper;
            captured.arguments = args == null ? new Object[0] : args;
            return (List<T>) result;
        }

        @Override
        public int update(String sql, Object... args) {
            updateCalled = true;
            return 0;
        }
    }
}
