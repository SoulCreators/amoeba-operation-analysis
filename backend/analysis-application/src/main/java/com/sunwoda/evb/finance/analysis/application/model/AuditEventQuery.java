package com.sunwoda.evb.finance.analysis.application.model;

import java.time.LocalDateTime;

/**
 * 审计事件只读查询条件。
 *
 * <p>本次新增：只表达现有 {@link AuditEvent} 字段的精确筛选，不定义
 * metadata 内部键语义，也不携带用户身份；身份必须由应用服务单独传入。</p>
 */
public final class AuditEventQuery {
    private final String eventId;
    private final String traceId;
    private final String operator;
    private final String service;
    private final String action;
    private final String objectType;
    private final String objectId;
    private final String result;
    private final LocalDateTime occurredFrom;
    private final LocalDateTime occurredTo;

    // 本次新增：允许按已有审计字段组合筛选，默认查询由上层显式授权后再执行。
    public AuditEventQuery(String eventId, String traceId, String operator,
                           String service, String action, String objectType,
                           String objectId, String result,
                           LocalDateTime occurredFrom, LocalDateTime occurredTo) {
        this.eventId = optional(eventId);
        this.traceId = optional(traceId);
        this.operator = optional(operator);
        this.service = optional(service);
        this.action = optional(action);
        this.objectType = optional(objectType);
        this.objectId = optional(objectId);
        this.result = optional(result);
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new IllegalArgumentException("occurredFrom 不能晚于 occurredTo");
        }
        this.occurredFrom = occurredFrom;
        this.occurredTo = occurredTo;
    }

    /** 本次新增：提供明确的全量查询构造方式，供已授权 ADMIN 的只读查询使用。 */
    public static AuditEventQuery all() {
        return new AuditEventQuery(null, null, null, null, null, null,
                null, null, null, null);
    }

    public String getEventId() { return eventId; }
    public String getTraceId() { return traceId; }
    public String getOperator() { return operator; }
    public String getService() { return service; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getResult() { return result; }
    public LocalDateTime getOccurredFrom() { return occurredFrom; }
    public LocalDateTime getOccurredTo() { return occurredTo; }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
