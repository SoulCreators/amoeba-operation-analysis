package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventQueryPort;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计查询应用层契约测试。
 *
 * <p>本次新增：锁定 ADMIN 全量只读、非 ADMIN 默认拒绝，以及追加写端口
 * 与查询端口并行存在的兼容边界。</p>
 */
class AuditEventQueryServiceTest {
    private static final String ADMIN_USER = "IT-ADMIN-001";
    private static final String FINANCE_USER = "FINANCE-001";

    @Test
    void adminCanQueryAllEventsAndTheServiceForwardsFiltersWithoutScopeClipping() {
        RecordingQueryPort queryPort = new RecordingQueryPort();
        AuditEventQuery query = new AuditEventQuery(null, " TRACE-001 ", null,
                "analysis-api", null, null, null, "SUCCESS",
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 31, 23, 59));
        AuditEvent event = event("EVT-001");
        queryPort.events.add(event);

        AuditEventQueryService service = service(queryPort, adminGrant());
        List<AuditEvent> result = service.query(ADMIN_USER, query);

        // 本次新增：ADMIN 查询直接获得端口返回的全部事件，查询条件原样下传。
        assertEquals(Collections.singletonList(event), result);
        assertSame(query, queryPort.receivedQuery);
        assertEquals(1, queryPort.invocations);
        assertThrows(UnsupportedOperationException.class, () -> result.add(event("EVT-002")));
    }

    @Test
    void nonAdminAndDisabledAdminAreDeniedBeforeTheReadPortIsTouched() {
        RecordingQueryPort queryPort = new RecordingQueryPort();
        AuditEventQueryService financeService = service(queryPort,
                new UserScope(FINANCE_USER, "HQ_FINANCE", AnalysisPerspective.BASE,
                        "ALL", true, true, false, true));

        // 本次新增：既有业务查看/明细/导出范围不推导为审计查询权限。
        assertThrows(IllegalStateException.class,
                () -> financeService.query(FINANCE_USER, AuditEventQuery.all()));
        assertEquals(0, queryPort.invocations);
        assertFalse(new AuditEventReadPolicy(permissions(
                new UserScope(ADMIN_USER, "ADMIN", AnalysisPerspective.BASE,
                        "ALL", true, true, false, false))).canReadAll(ADMIN_USER));
        assertThrows(IllegalArgumentException.class,
                () -> financeService.query(" ", AuditEventQuery.all()));
    }

    @Test
    void appendOnlyPortRemainsCallableWithoutDependingOnTheQueryPort() {
        RecordingAppendPort appendPort = new RecordingAppendPort();
        AuditEvent event = event("EVT-APPEND-001");

        // 本次新增：以既有 AuditEventPort 类型验证追加写契约未被查询端口替换或扩展破坏。
        appendPort.append(event);

        assertSame(event, appendPort.event);
    }

    @Test
    void queryModelNormalizesOptionalFieldsAndRejectsReversedTimeRange() {
        AuditEventQuery query = new AuditEventQuery(" EVT-001 ", " ", " operator ",
                null, null, null, null, null, null, null);

        // 本次新增：查询模型只规范化已有字段，不引入 metadata 结构或新的审计语义。
        assertEquals("EVT-001", query.getEventId());
        assertEquals("operator", query.getOperator());
        assertEquals(null, query.getTraceId());
        assertThrows(IllegalArgumentException.class, () -> new AuditEventQuery(
                null, null, null, null, null, null, null, null,
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)));
    }

    private AuditEventQueryService service(RecordingQueryPort queryPort,
                                           UserScope... grants) {
        return new AuditEventQueryService(new AuditEventReadPolicy(permissions(grants)), queryPort);
    }

    private PermissionService permissions(UserScope... grants) {
        InMemoryScopes repository = new InMemoryScopes();
        for (UserScope grant : grants) repository.save(grant);
        return new PermissionServiceImpl(repository);
    }

    private UserScope adminGrant() {
        return new UserScope(ADMIN_USER, "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, false, true);
    }

    private AuditEvent event(String eventId) {
        return new AuditEvent(eventId, "TRACE-001", "operator", "analysis-api",
                "QUERY", "audit_event", eventId, null, null, "SUCCESS", null,
                "{}", LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    private static final class RecordingQueryPort implements AuditEventQueryPort {
        private final List<AuditEvent> events = new ArrayList<AuditEvent>();
        private AuditEventQuery receivedQuery;
        private int invocations;

        @Override
        public List<AuditEvent> find(AuditEventQuery query) {
            receivedQuery = query;
            invocations++;
            return events;
        }
    }

    private static final class RecordingAppendPort implements AuditEventPort {
        private AuditEvent event;

        @Override
        public void append(AuditEvent event) { this.event = event; }
    }

    private static final class InMemoryScopes implements UserScopeRepository {
        private final List<UserScope> grants = new ArrayList<UserScope>();

        @Override
        public UserScope save(UserScope scope) {
            grants.add(scope);
            return scope;
        }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId,
                                                         AnalysisPerspective perspective) {
            if (userId == null || perspective == null) return Collections.emptyList();
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope grant : grants) {
                if (grant.getUserId().equals(userId) && grant.getPerspective() == perspective) {
                    result.add(grant);
                }
            }
            return result;
        }
    }
}
