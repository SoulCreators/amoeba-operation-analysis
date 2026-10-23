package com.sunwoda.evb.finance.analysis.domain.model;

/** 调整单口径：实际（ACT）或预算（BGT）。 */
public enum AdjustmentScenario {
    ACTUAL("ACT"),
    BUDGET("BGT");

    private final String code;

    AdjustmentScenario(String code) {
        this.code = code;
    }

    public String getCode() { return code; }

    public static AdjustmentScenario fromCode(String code) {
        for (AdjustmentScenario value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        throw new IllegalArgumentException("不支持的口径: " + code);
    }
}
