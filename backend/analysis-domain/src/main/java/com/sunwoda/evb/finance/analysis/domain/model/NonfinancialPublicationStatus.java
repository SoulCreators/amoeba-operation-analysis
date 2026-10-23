package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 非财经指标（电芯优率）发布状态。
 *
 * <p>与财务损益/PVM/AI 正式数字隔离，仅在同期无多巴长冲突时可由草稿进入已发布。</p>
 */
public enum NonfinancialPublicationStatus {
    DRAFT,
    PUBLISHED
}
