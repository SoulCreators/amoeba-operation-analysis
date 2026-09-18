package com.sunwoda.evb.finance.analysis.domain.model;

public enum AnalysisPerspective {
    BASE("BASE", "基地"),
    PRODUCT_LINE("PRODUCT_LINE", "产品线"),
    BUSINESS_UNIT("BUSINESS_UNIT", "事业部");

    private final String code;
    private final String displayName;

    AnalysisPerspective(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static AnalysisPerspective fromCode(String code) {
        for (AnalysisPerspective value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        throw new IllegalArgumentException("不支持的数据视角: " + code);
    }
}
