package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;

import java.time.LocalDateTime;

/**
 * Outbox 投递 Worker 的一次原子领取结果。
 *
 * <p>本次修改：把事件和领取令牌、租约时间一起传递给应用编排层；
 * Worker 不自行推断并发领取结果，状态端口必须保证令牌校验。</p>
 */
public final class CalculationEventDeliveryTask {
    private final CalculationLifecycleEvent event;
    private final String claimToken;
    private final LocalDateTime claimedAt;
    private final LocalDateTime leaseUntil;

    public CalculationEventDeliveryTask(CalculationLifecycleEvent event, String claimToken,
                                        LocalDateTime claimedAt, LocalDateTime leaseUntil) {
        if (event == null) throw new IllegalArgumentException("event 不能为空");
        this.event = event;
        this.claimToken = required(claimToken, "claimToken");
        if (claimedAt == null || leaseUntil == null) {
            throw new IllegalArgumentException("claimedAt 和 leaseUntil 不能为空");
        }
        if (leaseUntil.isBefore(claimedAt)) {
            throw new IllegalArgumentException("leaseUntil 不能早于 claimedAt");
        }
        this.claimedAt = claimedAt;
        this.leaseUntil = leaseUntil;
    }

    public CalculationLifecycleEvent getEvent() {
        return event;
    }

    public String getClaimToken() {
        return claimToken;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public LocalDateTime getLeaseUntil() {
        return leaseUntil;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
