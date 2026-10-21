package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationEventDeliveryTask;
import com.sunwoda.evb.finance.analysis.application.model.CalculationEventManualCommand;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventDeliveryPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationLifecycleEventPublisher;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryPolicy;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 计算生命周期 Outbox 投递应用服务。
 *
 * <p>本次新增：集中编排原子领取、可注入发布、失败退避、人工接管和人工操作边界；
 * 服务本身不连接数据库、不创建 HTTP 客户端，也不绕过状态端口直接修改状态。</p>
 */
public final class CalculationLifecycleEventDeliveryService {
    private final CalculationLifecycleEventDeliveryPort deliveryPort;
    private final CalculationLifecycleEventPublisher publisher;
    private final String workerId;
    private final int leaseMinutes;

    public CalculationLifecycleEventDeliveryService(CalculationLifecycleEventDeliveryPort deliveryPort,
                                                    CalculationLifecycleEventPublisher publisher,
                                                    String workerId, int leaseMinutes) {
        if (deliveryPort == null || publisher == null) {
            throw new IllegalArgumentException("Outbox 投递端口不能为空");
        }
        if (workerId == null || workerId.trim().isEmpty()) {
            throw new IllegalArgumentException("workerId 不能为空");
        }
        if (leaseMinutes <= 0) {
            throw new IllegalArgumentException("leaseMinutes 必须大于 0");
        }
        this.deliveryPort = deliveryPort;
        this.publisher = publisher;
        this.workerId = workerId.trim();
        this.leaseMinutes = leaseMinutes;
    }

    /**
     * 处理一批到期事件；单个事件失败不阻断同批次其他事件。
     */
    public void runOnce(LocalDateTime now, int limit) {
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        List<CalculationEventDeliveryTask> tasks = deliveryPort.claimReady(
                workerId, limit, now, now.plusMinutes(leaseMinutes));
        for (CalculationEventDeliveryTask task : tasks == null
                ? Collections.<CalculationEventDeliveryTask>emptyList() : tasks) {
            deliverOne(task, now);
        }
    }

    /** 保留人工重试入口；操作员和原因由命令强制携带，状态和审计由端口原子处理。 */
    public void manualRetry(CalculationEventManualCommand command, LocalDateTime operatedAt) {
        CalculationEventManualCommand checked = requiredCommand(command);
        deliveryPort.manualRetry(checked.getEventId(), checked.getOperator(), checked.getReason(),
                actualTime(operatedAt));
    }

    /** 保留人工补偿入口；补偿不是自动成功，必须由端口记录独立人工动作。 */
    public void manualCompensate(CalculationEventManualCommand command, LocalDateTime operatedAt) {
        CalculationEventManualCommand checked = requiredCommand(command);
        deliveryPort.manualCompensate(checked.getEventId(), checked.getOperator(), checked.getReason(),
                actualTime(operatedAt));
    }

    private void deliverOne(CalculationEventDeliveryTask task, LocalDateTime now) {
        if (task == null) return;
        try {
            publisher.publish(task.getEvent());
            // 本次修复：租约已过期或被接管时，成功回写返回 false；不能把旧 Worker 的结果再次转成失败重试。
            if (!deliveryPort.markDelivered(task.getEvent().getEventId(), task.getClaimToken(), now)) {
                return;
            }
        } catch (Exception failure) {
            markFailure(task, now, failure);
        }
    }

    private void markFailure(CalculationEventDeliveryTask task, LocalDateTime now, Exception failure) {
        String error = message(failure);
        CalculationLifecycleEvent event = task.getEvent();
        CalculationRetryDecision decision = CalculationRetryPolicy.decide(
                Math.max(1, event.getAttemptNo()), error, now);
        if (decision.isManualRequired()) {
            deliveryPort.markFailed(event.getEventId(), task.getClaimToken(), error,
                    now, now, true);
            return;
        }
        deliveryPort.markFailed(event.getEventId(), task.getClaimToken(), error,
                decision.getNextAttemptAt(), now, false);
    }

    private CalculationEventManualCommand requiredCommand(CalculationEventManualCommand command) {
        if (command == null) throw new IllegalArgumentException("人工命令不能为空");
        return command;
    }

    private LocalDateTime actualTime(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }

    private String message(Exception failure) {
        String detail = failure.getMessage();
        return detail == null || detail.trim().isEmpty()
                ? failure.getClass().getName() : detail;
    }
}
