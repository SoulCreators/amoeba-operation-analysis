package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算生命周期 Outbox 投递状态。
 *
 * <p>本次修改：与 V026 的数据库状态约束保持一致，不代表已经接入投递 Worker。</p>
 */
public enum CalculationEventDeliveryStatus {
    PENDING,
    IN_PROGRESS,
    DELIVERED,
    FAILED,
    MANUAL_REQUIRED
}
