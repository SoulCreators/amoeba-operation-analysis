package com.sunwoda.evb.finance.analysis.application.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditEventTest {
    @Test
    void keepsNullableTraceStateAndReasonFieldsAndDefaultsMetadata() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 19, 10, 15);

        AuditEvent event = new AuditEvent("EVT-001", " ", "user-1", "analysis-api",
                "CREATE", "export_task", "EXP-001", null, "QUEUED", "SUCCESS",
                null, null, occurredAt);

        assertNull(event.getTraceId());
        assertNull(event.getFromState());
        assertNull(event.getReason());
        assertEquals("{}", event.getMetadata());
        assertEquals(occurredAt, event.getOccurredAt());
    }

    @Test
    void rejectsMissingRequiredContractFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new AuditEvent(null, null, "operator", "service", "action",
                        "object", "id", null, null, "SUCCESS", null, "{}",
                        LocalDateTime.of(2026, 10, 19, 10, 15)));
        assertThrows(IllegalArgumentException.class, () ->
                new AuditEvent("EVT-001", null, "operator", "service", "action",
                        "object", "id", null, null, "SUCCESS", null, "{}", null));
    }

    @Test
    void preservesOpaqueMetadataJsonWithoutDefiningItsKeys() {
        AuditEvent event = event("{\"source\":\"test\",\"attempt\":1}");

        assertEquals("{\"source\":\"test\",\"attempt\":1}", event.getMetadata());
    }

    private static AuditEvent event(String metadata) {
        return new AuditEvent("EVT-001", "TRACE-001", "operator", "service", "action",
                "object", "object-1", "FROM", "TO", "SUCCESS", "reason", metadata,
                LocalDateTime.of(2026, 10, 19, 10, 15));
    }
}
