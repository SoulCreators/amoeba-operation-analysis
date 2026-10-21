package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算重试租约已经丢失或不再有效。
 *
 * <p>本次新增：将失去租约从普通业务失败中区分出来，Worker 必须跳过该任务，不能继续执行或覆盖新持有者结果。</p>
 */
public class CalculationRetryClaimLostException extends IllegalStateException {
    public CalculationRetryClaimLostException(String message) {
        super(message);
    }
}
