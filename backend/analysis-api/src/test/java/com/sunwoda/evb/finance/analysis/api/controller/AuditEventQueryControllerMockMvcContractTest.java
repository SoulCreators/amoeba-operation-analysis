package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventQueryPort;
import com.sunwoda.evb.finance.analysis.application.service.AuditEventQueryService;
import com.sunwoda.evb.finance.analysis.application.service.AuditEventReadPolicy;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 审计读取 HTTP 契约测试；本次新增：锁定 Principal 身份来源与 ADMIN 只读边界。 */
class AuditEventQueryControllerMockMvcContractTest {
    private static final String ADMIN_USER = "IT-ADMIN-001";
    private static final String NON_ADMIN_USER = "FINANCE-001";
    private PermissionService permissionService;
    private AuditEventQueryPort queryPort;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryPort = mock(AuditEventQueryPort.class);
        permissionService = mock(PermissionService.class);
        when(permissionService.list(anyString(), any(AnalysisPerspective.class))).thenAnswer(invocation ->
                ADMIN_USER.equals(invocation.getArgument(0))
                        ? Collections.singletonList(adminGrant()) : Collections.emptyList());
        AuditEventQueryService service = new AuditEventQueryService(
                new AuditEventReadPolicy(permissionService), queryPort);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditEventQueryController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void adminQueryMapsSupportedFiltersAndIgnoresForgedIdentityParameters() throws Exception {
        when(queryPort.find(any(AuditEventQuery.class))).thenReturn(Collections.singletonList(event("EVT-001")));
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("eventId", " EVT-001 ").param("traceId", "TRACE-001")
                        .param("occurredFrom", "2026-10-01T00:00:00")
                        .param("occurredTo", "2026-10-31T23:59:59")
                        .param("userId", "ATTACKER").param("role", "ADMIN")
                        .principal(principal(ADMIN_USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].eventId").value("EVT-001"));

        ArgumentCaptor<AuditEventQuery> captor = ArgumentCaptor.forClass(AuditEventQuery.class);
        verify(queryPort).find(captor.capture());
        assertEquals("EVT-001", captor.getValue().getEventId());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), captor.getValue().getOccurredFrom());
    }

    @Test
    void nonAdminCannotUseQueryRoleOrUserIdToPassAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").param("userId", ADMIN_USER)
                        .param("role", "ADMIN").principal(principal(NON_ADMIN_USER)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"));
        // 本次修复：授权策略会遍历三个已登记分析视角，契约测试按完整策略调用次数校验而非误判为重复调用。
        verify(permissionService, times(AnalysisPerspective.values().length))
                .list(eq(NON_ADMIN_USER), any(AnalysisPerspective.class));
        verifyNoInteractions(queryPort);
    }

    @Test
    void anonymousAndReversedTimeRangeAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs").param("userId", ADMIN_USER))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("A0002"));
        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("occurredFrom", "2026-10-31T00:00:00")
                        .param("occurredTo", "2026-10-01T00:00:00")
                        .principal(principal(ADMIN_USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("A0001"));
        verifyNoInteractions(queryPort);
    }

    private Principal principal(String name) { return () -> name; }

    private UserScope adminGrant() {
        return new UserScope(ADMIN_USER, "ADMIN", AnalysisPerspective.BASE,
                "ALL", true, true, false, true);
    }

    private AuditEvent event(String eventId) {
        return new AuditEvent(eventId, "TRACE-001", "operator", "analysis-api", "QUERY",
                "audit_event", eventId, null, null, "SUCCESS", null, "{}",
                LocalDateTime.of(2026, 10, 21, 10, 0));
    }
}
