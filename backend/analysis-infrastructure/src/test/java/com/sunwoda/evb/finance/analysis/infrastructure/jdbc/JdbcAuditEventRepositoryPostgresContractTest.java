package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GOV-007-S1-AUDIT-TECH-001：验证审计基础技术接缝的 PostgreSQL 落库现状。
 *
 * <p>本测试只验证 JdbcAuditEventRepository 到 audit.audit_event 的字段、JSONB、
 * 可空字段和 event_id 唯一性，不定义业务 action/result 词汇，也不引入业务幂等语义。</p>
 */
class JdbcAuditEventRepositoryPostgresContractTest {
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcAuditEventRepository repository;

    private String fixtureEventId;

    @BeforeAll
    static void connectToConfiguredPostgres() {
        // 本次修改：沿用项目真实 PostgreSQL 环境变量门禁，未配置数据库时不伪造通过结果。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; audit PostgreSQL contract was not run");

        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; audit PostgreSQL contract was not run");

        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null,
                "BLOCKED: AMOEBA_DB_PASSWORD is not set; audit PostgreSQL contract was not run");

        dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT event_id, trace_id, operator, service, action, object_type, "
                    + "object_id, from_state, to_state, result, reason, metadata, occurred_at "
                    + "FROM audit.audit_event LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: audit PostgreSQL schema is not available: "
                    + failure.getMessage());
        }
        repository = new JdbcAuditEventRepository(dataSource);
    }

    @AfterEach
    void cleanupFixtureRow() {
        // 本次修改：只删除当前测试随机 event_id，避免清理或污染数据库中的其他审计记录。
        if (jdbc == null || fixtureEventId == null) return;
        jdbc.update("DELETE FROM audit.audit_event WHERE event_id = ?", fixtureEventId);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE event_id = ?", Integer.class,
                fixtureEventId));
        fixtureEventId = null;
    }

    @Test
    void appendPersistsAllFieldsJsonMetadataAndNullableValues() {
        // 本次修改：使用真实 append 往返验证必填字段、JSONB metadata 及 trace/from/to/reason 的 null 落库。
        fixtureEventId = eventId("FIELDS");
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 20, 10, 15, 30);
        String metadata = "{\"source\":\"contract-test\",\"attempt\":1,"
                + "\"nested\":{\"enabled\":true}}";
        AuditEvent event = new AuditEvent(fixtureEventId, null, "operator-value", "service-value",
                "action-value", "object-type-value", "object-id-value", null, null,
                "result-value", null, metadata, occurredAt);

        repository.append(event);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT event_id, trace_id, operator, service, action, object_type, object_id, "
                        + "from_state, to_state, result, reason, occurred_at "
                        + "FROM audit.audit_event WHERE event_id = ?", fixtureEventId);
        assertEquals(fixtureEventId, row.get("event_id"));
        assertNull(row.get("trace_id"));
        assertEquals("operator-value", row.get("operator"));
        assertEquals("service-value", row.get("service"));
        assertEquals("action-value", row.get("action"));
        assertEquals("object-type-value", row.get("object_type"));
        assertEquals("object-id-value", row.get("object_id"));
        assertNull(row.get("from_state"));
        assertNull(row.get("to_state"));
        assertEquals("result-value", row.get("result"));
        assertNull(row.get("reason"));
        assertEquals(Timestamp.valueOf(occurredAt), row.get("occurred_at"));

        Boolean metadataMatches = jdbc.queryForObject(
                "SELECT metadata = ?::jsonb FROM audit.audit_event WHERE event_id = ?",
                Boolean.class, metadata, fixtureEventId);
        assertNotNull(metadataMatches);
        assertTrue(metadataMatches);
        assertTrue(jdbc.queryForObject(
                "SELECT metadata @> ?::jsonb FROM audit.audit_event WHERE event_id = ?",
                Boolean.class, "{\"nested\":{\"enabled\":true}}", fixtureEventId));
    }

    @Test
    void appendWithDuplicateEventIdKeepsCurrentDatabaseRejectionBehavior() {
        // 本次修改：只记录当前唯一 event_id 导致的数据库异常和单行结果，不把它扩展为业务幂等策略。
        fixtureEventId = eventId("DUPLICATE");
        AuditEvent event = new AuditEvent(fixtureEventId, null, "operator-value", "service-value",
                "action-value", "object-type-value", "object-id-value", null, null,
                "result-value", null, "{\"duplicateProbe\":true}",
                LocalDateTime.of(2026, 10, 20, 10, 16));

        repository.append(event);

        DataAccessException failure = assertThrows(DataAccessException.class,
                () -> repository.append(event));
        assertNotNull(failure);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit.audit_event WHERE event_id = ?", Integer.class,
                fixtureEventId));
        assertFalse(jdbc.queryForObject(
                "SELECT COUNT(*) > 1 FROM audit.audit_event WHERE event_id = ?", Boolean.class,
                fixtureEventId));
    }

    private static String eventId(String suffix) {
        // 本次修改：为每次数据库契约生成隔离的 event_id，避免与本机已有审计数据冲突。
        return "GOV-007-S1-AUDIT-TECH-001-" + suffix + "-"
                + UUID.randomUUID().toString().replace("-", "");
    }
}
