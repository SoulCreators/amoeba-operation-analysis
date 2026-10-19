package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcAuditEventRepositoryTest {
    @Test
    void appendsAllContractFieldsWithJsonbCast() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcAuditEventRepository repository = new JdbcAuditEventRepository(jdbc);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 19, 10, 15);
        AuditEvent event = new AuditEvent("EVT-001", null, "operator", "service", "UPDATE",
                "export_task", "EXP-001", "QUEUED", "SUCCEEDED", "SUCCESS", null,
                "{\"source\":\"test\"}", occurredAt);

        final Object[][] capturedInvocation = new Object[1][];
        doAnswer(invocation -> {
            capturedInvocation[0] = invocation.getArguments();
            return 1;
        }).when(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any());

        repository.append(event);

        Object[] captured = capturedInvocation[0];
        assertEquals(14, captured.length);
        assertTrue(((String) captured[0]).contains("audit.audit_event"));
        assertEquals("EVT-001", captured[1]);
        assertEquals(null, captured[2]);
        assertEquals("operator", captured[3]);
        assertEquals("service", captured[4]);
        assertEquals("UPDATE", captured[5]);
        assertEquals("export_task", captured[6]);
        assertEquals("EXP-001", captured[7]);
        assertEquals("QUEUED", captured[8]);
        assertEquals("SUCCEEDED", captured[9]);
        assertEquals("SUCCESS", captured[10]);
        assertEquals(null, captured[11]);
        assertEquals("{\"source\":\"test\"}", captured[12]);
        assertEquals(Timestamp.valueOf(occurredAt), captured[13]);
    }

    @Test
    void rejectsNullEventBeforeTouchingJdbc() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThrows(IllegalArgumentException.class,
                () -> new JdbcAuditEventRepository(jdbc).append(null));

        verify(jdbc, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }
}
