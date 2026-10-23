package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简易调整单不可变领域模型。
 *
 * <p>调整是叠加在普通计算结果之上的独立计算层，金额与方向只作用于指定
 * 口径 + 报表项 + 范围，不修改源事实行，也不自动复制到其他数据集。
 * 已发布结果不受调整影响，调整生效需创建新计算版本。</p>
 */
public final class Adjustment {
    private final String adjustmentId;
    private final String period;
    private final AnalysisPerspective perspective;
    private final String scopeCode;
    private final AdjustmentScenario scenario;
    private final String metricCode;
    private final BigDecimal amount;
    private final AdjustmentDirection direction;
    private final String reason;
    private final String evidenceFileId;
    private final String expectedVersion;
    private final String enteredBy;
    private final AdjustmentStatus status;
    private final LocalDateTime enteredAt;
    private final String voidedBy;
    private final LocalDateTime voidedAt;
    private final String voidReason;

    private Adjustment(String adjustmentId, String period, AnalysisPerspective perspective,
                       String scopeCode, AdjustmentScenario scenario, String metricCode,
                       BigDecimal amount, AdjustmentDirection direction, String reason,
                       String evidenceFileId, String expectedVersion, String enteredBy,
                       AdjustmentStatus status, LocalDateTime enteredAt,
                       String voidedBy, LocalDateTime voidedAt, String voidReason) {
        this.adjustmentId = adjustmentId;
        this.period = period;
        this.perspective = perspective;
        this.scopeCode = scopeCode;
        this.scenario = scenario;
        this.metricCode = metricCode;
        this.amount = amount;
        this.direction = direction;
        this.reason = reason;
        this.evidenceFileId = evidenceFileId;
        this.expectedVersion = expectedVersion;
        this.enteredBy = enteredBy;
        this.status = status;
        this.enteredAt = enteredAt;
        this.voidedBy = voidedBy;
        this.voidedAt = voidedAt;
        this.voidReason = voidReason;
    }

    public static Adjustment create(String adjustmentId, String period, AnalysisPerspective perspective,
                                    String scopeCode, AdjustmentScenario scenario, String metricCode,
                                    BigDecimal amount, AdjustmentDirection direction, String reason,
                                    String evidenceFileId, String expectedVersion, String enteredBy,
                                    LocalDateTime enteredAt) {
        return new Adjustment(
                required(adjustmentId, "adjustmentId"),
                required(period, "period"),
                perspective,
                required(scopeCode, "scopeCode"),
                scenario,
                required(metricCode, "metricCode"),
                requirePositive(amount),
                direction,
                required(reason, "reason"),
                optional(evidenceFileId),
                optional(expectedVersion),
                required(enteredBy, "enteredBy"),
                AdjustmentStatus.DRAFT,
                enteredAt == null ? LocalDateTime.now() : enteredAt,
                null, null, null);
    }

    /** 从持久化还原完整调整单（含状态与作废痕迹）。 */
    public static Adjustment restore(String adjustmentId, String period, AnalysisPerspective perspective,
                                     String scopeCode, AdjustmentScenario scenario, String metricCode,
                                     BigDecimal amount, AdjustmentDirection direction, String reason,
                                     String evidenceFileId, String expectedVersion, String enteredBy,
                                     AdjustmentStatus status, LocalDateTime enteredAt,
                                     String voidedBy, LocalDateTime voidedAt, String voidReason) {
        return new Adjustment(
                required(adjustmentId, "adjustmentId"),
                required(period, "period"),
                perspective,
                required(scopeCode, "scopeCode"),
                scenario,
                required(metricCode, "metricCode"),
                requirePositive(amount),
                direction,
                required(reason, "reason"),
                optional(evidenceFileId),
                optional(expectedVersion),
                required(enteredBy, "enteredBy"),
                status == null ? AdjustmentStatus.DRAFT : status,
                enteredAt,
                optional(voidedBy), voidedAt, optional(voidReason));
    }

    public Adjustment activate() {
        if (status != AdjustmentStatus.DRAFT) {
            throw new IllegalStateException("只有草稿调整可以生效: " + status);
        }
        return new Adjustment(adjustmentId, period, perspective, scopeCode, scenario, metricCode,
                amount, direction, reason, evidenceFileId, expectedVersion, enteredBy,
                AdjustmentStatus.ACTIVE, enteredAt, voidedBy, voidedAt, voidReason);
    }

    public Adjustment voidAdjustment(String operator, LocalDateTime at, String why) {
        if (status != AdjustmentStatus.ACTIVE) {
            throw new IllegalStateException("只有已生效调整可以作废: " + status);
        }
        return new Adjustment(adjustmentId, period, perspective, scopeCode, scenario, metricCode,
                amount, direction, reason, evidenceFileId, expectedVersion, enteredBy,
                AdjustmentStatus.VOID, enteredAt,
                required(operator, "voidedBy"), at == null ? LocalDateTime.now() : at,
                required(why, "voidReason"));
    }

    /** 方向作用于金额得到带符号贡献值，供叠加层求和。 */
    public BigDecimal signedAmount() {
        return direction == AdjustmentDirection.DECREASE ? amount.negate() : amount;
    }

    public String getAdjustmentId() { return adjustmentId; }
    public String getPeriod() { return period; }
    public AnalysisPerspective getPerspective() { return perspective; }
    public String getScopeCode() { return scopeCode; }
    public AdjustmentScenario getScenario() { return scenario; }
    public String getMetricCode() { return metricCode; }
    public BigDecimal getAmount() { return amount; }
    public AdjustmentDirection getDirection() { return direction; }
    public String getReason() { return reason; }
    public String getEvidenceFileId() { return evidenceFileId; }
    public String getExpectedVersion() { return expectedVersion; }
    public String getEnteredBy() { return enteredBy; }
    public AdjustmentStatus getStatus() { return status; }
    public LocalDateTime getEnteredAt() { return enteredAt; }
    public String getVoidedBy() { return voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public String getVoidReason() { return voidReason; }
    public boolean isActive() { return status == AdjustmentStatus.ACTIVE; }
    public boolean isDraft() { return status == AdjustmentStatus.DRAFT; }
    public boolean isVoided() { return status == AdjustmentStatus.VOID; }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount 不能为空");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("调整金额必须为非负数，方向由 direction 表达");
        }
        return value;
    }
}
