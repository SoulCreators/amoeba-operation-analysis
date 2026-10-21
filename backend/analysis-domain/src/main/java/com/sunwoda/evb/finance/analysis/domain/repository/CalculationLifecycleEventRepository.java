package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 计算生命周期事件 Outbox 仓储契约。
 *
 * <p>本次修改：提供同事务追加、按事件读取和待投递领取所需的最小纯领域端口；
 * 具体锁、事务、投递和补偿策略由后续基础设施切片实现。</p>
 */
public interface CalculationLifecycleEventRepository {
    CalculationLifecycleEvent save(CalculationLifecycleEvent event);

    Optional<CalculationLifecycleEvent> findByEventId(String eventId);

    List<CalculationLifecycleEvent> findReadyForDelivery(
            CalculationEventDeliveryStatus status, LocalDateTime now, int limit);
}
