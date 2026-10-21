package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventDeliveryTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 状态端口。
 *
 * <p>本次修改：把数据库领取、令牌校验、投递结果状态更新和人工动作边界隔离在端口之后；
 * 基础设施实现必须用原子领取和 claimToken 防止多 Worker 重复处理。</p>
 */
public interface CalculationLifecycleEventDeliveryPort {
    /**
     * 原子领取到期事件。返回的事件 attemptNo 必须是本次投递的 1-based 尝试号，
     * 初次领取应由 0 增加为 1；领取过程必须支持租约过期接管。
     */
    List<CalculationEventDeliveryTask> claimReady(String workerId, int limit,
                                                  LocalDateTime now, LocalDateTime leaseUntil);

    /** 仅允许持有本次领取令牌的 Worker 将 IN_PROGRESS 置为 DELIVERED。 */
    boolean markDelivered(String eventId, String claimToken, LocalDateTime deliveredAt);

    /**
     * 仅允许持有本次领取令牌的 Worker 记录失败；terminal=true 时必须落为 MANUAL_REQUIRED。
     */
    boolean markFailed(String eventId, String claimToken, String errorMessage,
                       LocalDateTime nextAttemptAt, LocalDateTime failedAt, boolean terminal);

    /**
     * 人工重试入口。实现必须只接受 MANUAL_REQUIRED，并在同一审计边界内记录操作者、原因和时间。
     */
    void manualRetry(String eventId, String operator, String reason, LocalDateTime operatedAt);

    /**
     * 人工补偿入口。实现必须只接受 MANUAL_REQUIRED，并在同一审计边界内记录操作者、原因和时间。
     */
    void manualCompensate(String eventId, String operator, String reason, LocalDateTime operatedAt);
}
