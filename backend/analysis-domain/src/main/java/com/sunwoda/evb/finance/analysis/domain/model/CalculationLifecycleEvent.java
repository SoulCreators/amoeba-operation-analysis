package com.sunwoda.evb.finance.analysis.domain.model;

import java.time.LocalDateTime;

/**
 * 计算生命周期事件 Outbox 的不可变领域模型。
 *
 * <p>本次修改：表达事件待发送、重试和人工接管所需的最小字段；
 * 外部投递、告警和补偿执行不在本领域模型中实现。</p>
 */
public final class CalculationLifecycleEvent {
    private final String eventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String calculationVersionId;
    private final String traceId;
    private final String payloadJson;
    private final CalculationEventDeliveryStatus deliveryStatus;
    private final int attemptNo;
    private final LocalDateTime nextAttemptAt;
    private final String lastError;
    private final LocalDateTime occurredAt;
    private final LocalDateTime deliveredAt;

    public CalculationLifecycleEvent(String eventId, String aggregateType, String aggregateId,
                                     String eventType, String calculationVersionId,
                                     String traceId, String payloadJson,
                                     CalculationEventDeliveryStatus deliveryStatus,
                                     int attemptNo, LocalDateTime nextAttemptAt,
                                     String lastError, LocalDateTime occurredAt,
                                     LocalDateTime deliveredAt) {
        this.eventId = requiredText(eventId, "eventId");
        this.aggregateType = requiredText(aggregateType, "aggregateType");
        this.aggregateId = requiredText(aggregateId, "aggregateId");
        this.eventType = requiredText(eventType, "eventType");
        this.calculationVersionId = optionalText(calculationVersionId);
        this.traceId = optionalText(traceId);
        this.payloadJson = requiredText(payloadJson, "payloadJson");
        if (deliveryStatus == null) {
            throw new IllegalArgumentException("deliveryStatus 不能为空");
        }
        this.deliveryStatus = deliveryStatus;
        if (attemptNo < 0) {
            throw new IllegalArgumentException("attemptNo 不能小于 0");
        }
        this.attemptNo = attemptNo;
        if (nextAttemptAt == null || occurredAt == null) {
            throw new IllegalArgumentException("nextAttemptAt 和 occurredAt 不能为空");
        }
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = optionalText(lastError);
        this.occurredAt = occurredAt;
        this.deliveredAt = deliveredAt;
        validateDeliveryFields();
    }

    public String getEventId() {
        return eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getCalculationVersionId() {
        return calculationVersionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public CalculationEventDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    private void validateDeliveryFields() {
        if (deliveryStatus == CalculationEventDeliveryStatus.DELIVERED && deliveredAt == null) {
            throw new IllegalArgumentException("DELIVERED 事件必须有 deliveredAt");
        }
        if (deliveryStatus != CalculationEventDeliveryStatus.DELIVERED && deliveredAt != null) {
            throw new IllegalArgumentException("未投递完成事件不能有 deliveredAt");
        }
        if ((deliveryStatus == CalculationEventDeliveryStatus.FAILED
                || deliveryStatus == CalculationEventDeliveryStatus.MANUAL_REQUIRED)
                && (lastError == null || lastError.trim().isEmpty())) {
            throw new IllegalArgumentException("失败或人工接管事件必须保留 lastError");
        }
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }

    private static String optionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
