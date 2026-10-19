package com.sunwoda.evb.finance.analysis.application.model;

import java.time.LocalDateTime;

/**
 * Minimal, append-only audit record contract.
 *
 * <p>The metadata value is an opaque JSON object. Its keys and business
 * meaning are intentionally left to the caller so this foundation contract
 * does not claim a complete audit vocabulary.</p>
 */
public final class AuditEvent {
    private final String eventId;
    private final String traceId;
    private final String operator;
    private final String service;
    private final String action;
    private final String objectType;
    private final String objectId;
    private final String fromState;
    private final String toState;
    private final String result;
    private final String reason;
    private final String metadata;
    private final LocalDateTime occurredAt;

    public AuditEvent(String eventId, String traceId, String operator, String service,
                      String action, String objectType, String objectId,
                      String fromState, String toState, String result, String reason,
                      String metadata, LocalDateTime occurredAt) {
        this.eventId = required(eventId, "eventId");
        this.traceId = optional(traceId);
        this.operator = required(operator, "operator");
        this.service = required(service, "service");
        this.action = required(action, "action");
        this.objectType = required(objectType, "objectType");
        this.objectId = required(objectId, "objectId");
        this.fromState = optional(fromState);
        this.toState = optional(toState);
        this.result = required(result, "result");
        this.reason = optional(reason);
        this.metadata = metadata == null || metadata.trim().isEmpty() ? "{}" : metadata.trim();
        this.occurredAt = required(occurredAt, "occurredAt");
    }

    public String getEventId() { return eventId; }
    public String getTraceId() { return traceId; }
    public String getOperator() { return operator; }
    public String getService() { return service; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public String getMetadata() { return metadata; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static LocalDateTime required(LocalDateTime value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
