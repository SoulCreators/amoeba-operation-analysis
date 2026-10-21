package com.sunwoda.evb.finance.analysis.application.model;

/**
 * Outbox 人工处理命令。
 *
 * <p>本次修改：人工重试和人工补偿必须携带操作者、原因和请求边界；
 * 应用服务只暴露显式入口，不允许 Worker 通过自动失败路径伪造人工动作。</p>
 */
public final class CalculationEventManualCommand {
    private final String eventId;
    private final String operator;
    private final String reason;

    public CalculationEventManualCommand(String eventId, String operator, String reason) {
        this.eventId = required(eventId, "eventId");
        this.operator = required(operator, "operator");
        this.reason = required(reason, "reason");
    }

    public String getEventId() {
        return eventId;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
