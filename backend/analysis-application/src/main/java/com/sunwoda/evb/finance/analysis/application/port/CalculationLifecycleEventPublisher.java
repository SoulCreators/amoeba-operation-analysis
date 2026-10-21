package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;

/**
 * Outbox 外部投递端口。
 *
 * <p>本次修改：Worker 只依赖抽象发布能力；HTTP、消息队列或内部服务客户端由部署适配器注入，
 * 本模块不臆造外部协议。</p>
 */
@FunctionalInterface
public interface CalculationLifecycleEventPublisher {
    void publish(CalculationLifecycleEvent event);
}
