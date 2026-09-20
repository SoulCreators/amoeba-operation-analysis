package com.sunwoda.evb.finance.analysis.domain.model;

import java.math.BigDecimal;

public class PnlFact {
    private final String scopeCode;
    private final BigDecimal volume;
    private final BigDecimal revenue;
    private final BigDecimal salesCost;
    private final BigDecimal idleExpense;
    private final BigDecimal baseExpense;
    private final BigDecimal rdExpense;
    private final BigDecimal assetImpairment;
    private final BigDecimal creditImpairment;
    private final BigDecimal otherIncome;

    public PnlFact(String scopeCode, BigDecimal volume, BigDecimal revenue,
                   BigDecimal salesCost, BigDecimal idleExpense, BigDecimal baseExpense,
                   BigDecimal rdExpense, BigDecimal assetImpairment,
                   BigDecimal creditImpairment, BigDecimal otherIncome) {
        this.scopeCode = scopeCode;
        this.volume = value(volume);
        this.revenue = value(revenue);
        this.salesCost = value(salesCost);
        this.idleExpense = value(idleExpense);
        this.baseExpense = value(baseExpense);
        this.rdExpense = value(rdExpense);
        this.assetImpairment = value(assetImpairment);
        this.creditImpairment = value(creditImpairment);
        this.otherIncome = value(otherIncome);
    }

    public String getScopeCode() { return scopeCode; }
    public BigDecimal getVolume() { return volume; }
    public BigDecimal getRevenue() { return revenue; }
    public BigDecimal getSalesCost() { return salesCost; }
    public BigDecimal getIdleExpense() { return idleExpense; }
    public BigDecimal getBaseExpense() { return baseExpense; }
    public BigDecimal getRdExpense() { return rdExpense; }
    public BigDecimal getAssetImpairment() { return assetImpairment; }
    public BigDecimal getCreditImpairment() { return creditImpairment; }
    public BigDecimal getOtherIncome() { return otherIncome; }

    private BigDecimal value(BigDecimal number) {
        return number == null ? BigDecimal.ZERO : number;
    }
}
