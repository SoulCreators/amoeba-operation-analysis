package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算结果版本头的生命周期状态。
 *
 * <p>本次修改：为结果版本数据底座补充历史版本可识别的纯领域状态；
 * 应用编排、权限校验和持久化适配仍由后续切片接入。</p>
 */
public enum CalculationVersionStatus {
    CALCULATED,
    CONFIRMED,
    PUBLISHED,
    SUPERSEDED
}
