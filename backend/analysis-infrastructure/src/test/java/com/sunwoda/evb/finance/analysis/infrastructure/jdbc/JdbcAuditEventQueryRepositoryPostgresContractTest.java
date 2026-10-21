package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * JdbcAuditEventQueryRepository 真实 PostgreSQL 查询契约测试。
 *
 * <p>本次新增：验证固定字段往返、精确过滤、[from,to) 时间边界和排序；数据库缺失时明确记录未执行。</p>
 */
class JdbcAuditEventQueryRepositoryPostgresContractTest {
    private static JdbcTemplate jdbc;
    private static JdbcAuditEventRepository appendRepository;
    private static JdbcAuditEventQueryRepository queryRepository;
    private final List<String> fixtureEventIds = new ArrayList<String>();

    @BeforeAll
    static void connectToConfiguredPostgres() {
        // 本次新增：沿用项目 PostgreSQL 环境变量门禁，数据库缺失时明确跳过而不是改用内存替代。
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; audit query PostgreSQL contract was not executed");
        String username = System.getenv("AMOEBA_DB_USERNAME");
        assumeTrue(username != null && !username.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_USERNAME is not set; audit query PostgreSQL contract was not executed");
        String password = System.getenv("AMOEBA_DB_PASSWORD");
        assumeTrue(password != null, "BLOCKED: AMOEBA_DB_PASSWORD is not set; audit query PostgreSQL contract was not executed");
        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT event_id, trace_id, operator, service, action, object_type, object_id, "
                    + "from_state, to_state, result, reason, metadata, occurred_at FROM audit.audit_event LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: audit PostgreSQL schema is not available; contract was not executed: "
                    + failure.getMessage());
        }
        appendRepository = new JdbcAuditEventRepository(dataSource);
        queryRepository = new JdbcAuditEventQueryRepository(dataSource);
    }

    @AfterEach
    void cleanupFixtureRows() {
        if (jdbc == null || fixtureEventIds.isEmpty()) return;
        for (String eventId : fixtureEventIds) {
            jdbc.update("DELETE FROM audit.audit_event WHERE event_id = ?", eventId);
        }
        fixtureEventIds.clear();
    }

    @Test
    void findsExactFieldsWithHalfOpenTimeRangeAndStableDescendingOrder() {
        LocalDateTime from = LocalDateTime.of(2026, 10, 21, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 10, 21, 11, 0);
        String prefix = "GOV-007-S1-AUDIT-QUERY-" + UUID.randomUUID().toString().replace("-", "");
        String eventA = fixture(prefix + "-A");
        String eventB = fixture(prefix + "-B");
        String eventC = fixture(prefix + "-C");
        String upper = fixture(prefix + "-UPPER");
        String mismatch = fixture(prefix + "-MISMATCH");
        append(eventA, "TRACE-QUERY", "operator-query", "analysis-api", "UPDATE", "export_task", "EXP-QUERY", "SUCCESS", from);
        append(eventB, "TRACE-QUERY", "operator-query", "analysis-api", "UPDATE", "export_task", "EXP-QUERY", "SUCCESS", from);
        append(eventC, "TRACE-QUERY", "operator-query", "analysis-api", "UPDATE", "export_task", "EXP-QUERY", "SUCCESS", from.plusMinutes(30));
        append(upper, "TRACE-QUERY", "operator-query", "analysis-api", "UPDATE", "export_task", "EXP-QUERY", "SUCCESS", to);
        append(mismatch, "TRACE-QUERY", "operator-query", "analysis-api", "DELETE", "export_task", "EXP-QUERY", "SUCCESS", from.plusMinutes(20));

        List<AuditEvent> events = queryRepository.find(new AuditEventQuery(null, "TRACE-QUERY",
                "operator-query", "analysis-api", "UPDATE", "export_task", "EXP-QUERY", "SUCCESS", from, to));

        // 本次新增：先按 occurred_at 倒序，再按 event_id 倒序；from 包含而 to 排除。
        assertEquals(Arrays.asList(eventC, eventB, eventA), eventIds(events));
        assertEquals("{\"source\":\"query-contract\"}", events.get(0).getMetadata());
        assertTrue(events.stream().allMatch(event -> event.getOccurredAt().isBefore(to)));
    }

    private void append(String eventId, String traceId, String operator, String service,
                        String action, String objectType, String objectId, String result,
                        LocalDateTime occurredAt) {
        // 本次新增：复用既有 append 适配器准备真实数据，避免修改既有写入逻辑。
        appendRepository.append(new AuditEvent(eventId, traceId, operator, service, action,
                objectType, objectId, "QUEUED", "SUCCEEDED", result, null,
                "{\"source\":\"query-contract\"}", occurredAt));
    }

    private String fixture(String eventId) {
        fixtureEventIds.add(eventId);
        return eventId;
    }

    private static List<String> eventIds(List<AuditEvent> events) {
        List<String> result = new ArrayList<String>();
        for (AuditEvent event : events) result.add(event.getEventId());
        return result;
    }
}
