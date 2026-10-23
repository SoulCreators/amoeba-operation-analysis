package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;

/**
 * 非财经指标（电芯优率）配对结果。
 *
 * <p>差额＝实际−目标；达成率＝实际÷目标（目标为 0 或缺侧时为 null，不自动补 0）。
 * 巴长工号由型号映射解析，多巴长冲突标记 {@link #isLeaderConflict()}，冲突阻断发布。</p>
 */
public class NonfinancialResult {
    private final Long id;
    private final String period;
    private final String baseCode;
    private final String productionLine;
    private final String cellModel;
    private final String qualityCategory;
    private final BigDecimal targetValue;
    private final BigDecimal actualValue;
    private final BigDecimal gap;
    private final BigDecimal achievementRate;
    private final PairingStatus pairingStatus;
    private final String leaderEmployeeNo;
    private final boolean leaderConflict;

    public NonfinancialResult(Long id, String period, String baseCode, String productionLine,
                              String cellModel, String qualityCategory, BigDecimal targetValue,
                              BigDecimal actualValue, BigDecimal gap, BigDecimal achievementRate,
                              PairingStatus pairingStatus, String leaderEmployeeNo,
                              boolean leaderConflict) {
        if (pairingStatus == null) throw new IllegalArgumentException("配对状态不能为空");
        this.id = id;
        this.period = requireText(period, "period");
        this.baseCode = requireText(baseCode, "baseCode");
        this.productionLine = normalizeNullable(productionLine);
        this.cellModel = requireText(cellModel, "cellModel");
        this.qualityCategory = requireText(qualityCategory, "qualityCategory");
        this.targetValue = targetValue;
        this.actualValue = actualValue;
        this.gap = gap;
        this.achievementRate = achievementRate;
        this.pairingStatus = pairingStatus;
        this.leaderEmployeeNo = leaderEmployeeNo;
        this.leaderConflict = leaderConflict;
    }

    public Long getId() { return id; }
    public String getPeriod() { return period; }
    public String getBaseCode() { return baseCode; }
    public String getProductionLine() { return productionLine; }
    public String getCellModel() { return cellModel; }
    public String getQualityCategory() { return qualityCategory; }
    public BigDecimal getTargetValue() { return targetValue; }
    public BigDecimal getActualValue() { return actualValue; }
    public BigDecimal getGap() { return gap; }
    public BigDecimal getAchievementRate() { return achievementRate; }
    public PairingStatus getPairingStatus() { return pairingStatus; }
    public String getLeaderEmployeeNo() { return leaderEmployeeNo; }
    public boolean isLeaderConflict() { return leaderConflict; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static String normalizeNullable(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
