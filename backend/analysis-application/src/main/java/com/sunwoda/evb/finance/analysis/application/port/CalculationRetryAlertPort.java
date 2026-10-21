package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;

/**
 * 计算重试人工接管告警端口。
 *
 * <p>本次新增：将 MANUAL_REQUIRED 告警从应用编排中抽成端口，避免应用层依赖具体日志或告警技术实现。</p>
 */
public interface CalculationRetryAlertPort {
    /**
     * 告知外部系统某次计算失败已需要人工接管。
     *
     * <p>本次新增：调用发生在失败状态持久化成功之后，告警实现不得反向修改计算重试状态。</p>
     */
    void alert(CalculationJob job, CalculationRetryDecision decision);
}
