package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

/**
 * 人工修订请求幂等应用端口。
 *
 * <p>本次新增：把 requestKey 的领取、冲突判断、结果重放和结果持久化交给应用端口；
 * 生产实现必须使用持久化唯一约束或等价的原子机制，不能用进程内 Map 冒充多实例幂等。</p>
 */
public interface ManualRevisionIdempotencyPort {

    /**
     * 原子执行或重放一次人工修订请求。
     *
     * <p>实现必须以 userId、requestKey、sourceVersionId 和 revisionReason 作为可审计请求边界；
     * 相同请求重放已保存结果，不同载荷冲突失败，未接入持久实现时必须失败关闭。</p>
     */
    CalculationVersion execute(String userId,
                               String requestKey,
                               String sourceVersionId,
                               String revisionReason,
                               RevisionOperation operation);

    /** 本次新增：由人工修订服务提供真正的版本追加操作，端口实现负责幂等边界。 */
    interface RevisionOperation {
        CalculationVersion execute();
    }
}
