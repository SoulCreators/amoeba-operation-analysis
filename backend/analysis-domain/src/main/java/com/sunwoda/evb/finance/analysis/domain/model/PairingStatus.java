package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 目标/实际配对状态：同键两侧齐全、仅目标、仅实际。
 *
 * <p>仅一侧是正常状态，缺侧以空值呈现，不自动补 0；多巴长冲突单独由结果标记。</p>
 */
public enum PairingStatus {
    BOTH,
    TARGET_ONLY,
    ACTUAL_ONLY
}
