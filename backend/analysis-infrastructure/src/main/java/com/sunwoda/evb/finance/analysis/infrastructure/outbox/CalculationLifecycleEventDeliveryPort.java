package com.sunwoda.evb.finance.analysis.infrastructure.outbox;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 投递协调端口。
 *
 * <p>本次新增：独立于既有事件追加/查询仓储，提供原子领取、租约恢复以及令牌 CAS 确认能力，
 * 供后续 Worker 接入；业务应用不需要感知 PostgreSQL 锁和协调表。</p>
 */
public interface CalculationLifecycleEventDeliveryPort {
    /**
     * 原子领取到期事件，并把事件状态置为 IN_PROGRESS。
     */
    List<CalculationLifecycleEventDeliveryClaim> claimReadyForDelivery(
            LocalDateTime now, int limit, Duration leaseDuration);

    /** 本次补充：允许应用层传入可审计的 Worker 标识，旧调用保持兼容。 */
    default List<CalculationLifecycleEventDeliveryClaim> claimReadyForDelivery(
            String workerId, LocalDateTime now, int limit, Duration leaseDuration) {
        return claimReadyForDelivery(now, limit, leaseDuration);
    }

    /**
     * 使用领取令牌确认投递成功；令牌过期或已被接管时返回 false。
     */
    boolean markDelivered(String eventId, String claimToken, LocalDateTime deliveredAt);

    /**
     * 使用领取令牌更新失败状态；状态只允许 FAILED 或 MANUAL_REQUIRED。
     */
    boolean markFailed(String eventId, String claimToken, CalculationEventDeliveryStatus failureStatus,
                       String lastError, LocalDateTime nextAttemptAt);
}
