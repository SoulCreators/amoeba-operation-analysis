package com.sunwoda.evb.finance.analysis.domain.model;

/** 调整单生命周期：草稿 → 生效；生效可作废。 */
public enum AdjustmentStatus {
    DRAFT,
    ACTIVE,
    VOID
}
