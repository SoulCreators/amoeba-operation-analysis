package com.sunwoda.evb.finance.analysis.infrastructure.outbox;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;

import java.time.LocalDateTime;

/**
 * Outbox 投递领取结果。
 *
 * <p>本次新增：把事件正文与数据库领取令牌一起交给 Worker，后续成功或失败确认必须携带同一令牌，
 * 从而避免租约过期后旧 Worker 覆盖新 Worker 的处理结果。</p>
 */
public final class CalculationLifecycleEventDeliveryClaim {
    private final CalculationLifecycleEvent event;
    private final String workerId;
    private final String claimToken;
    private final LocalDateTime claimedUntil;

    public CalculationLifecycleEventDeliveryClaim(CalculationLifecycleEvent event, String workerId,
                                                  String claimToken, LocalDateTime claimedUntil) {
        if (event == null) {
            throw new IllegalArgumentException("event 不能为空");
        }
        this.event = event;
        this.workerId = required(workerId, "workerId");
        this.claimToken = required(claimToken, "claimToken");
        if (claimedUntil == null) {
            throw new IllegalArgumentException("claimedUntil 不能为空");
        }
        this.claimedUntil = claimedUntil;
    }

    public CalculationLifecycleEvent getEvent() {
        return event;
    }

    public String getWorkerId() {
        return workerId;
    }

    public String getClaimToken() {
        return claimToken;
    }

    public LocalDateTime getClaimedUntil() {
        return claimedUntil;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
