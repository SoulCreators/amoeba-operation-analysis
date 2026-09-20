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
    private final BigDecimal budgetVolume;
    private final BigDecimal budgetRevenue;
    private final BigDecimal budgetSalesCost;
    private final BigDecimal budgetIdleExpense;
    private final BigDecimal budgetBaseExpense;
    private final BigDecimal budgetRdExpense;
    private final BigDecimal budgetAssetImpairment;
    private final BigDecimal budgetCreditImpairment;
    private final BigDecimal budgetOtherIncome;

    public PnlFact(String scopeCode, BigDecimal volume, BigDecimal revenue,
                   BigDecimal salesCost, BigDecimal idleExpense, BigDecimal baseExpense,
                   BigDecimal rdExpense, BigDecimal assetImpairment,
                   BigDecimal creditImpairment, BigDecimal otherIncome) {
        this(scopeCode, volume, revenue, salesCost, idleExpense, baseExpense,
                rdExpense, assetImpairment, creditImpairment, otherIncome,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    public PnlFact(String scopeCode, BigDecimal volume, BigDecimal revenue,
                   BigDecimal salesCost, BigDecimal idleExpense, BigDecimal baseExpense,
                   BigDecimal rdExpense, BigDecimal assetImpairment,
                   BigDecimal creditImpairment, BigDecimal otherIncome,
                   BigDecimal budgetVolume, BigDecimal budgetRevenue,
                   BigDecimal budgetSalesCost, BigDecimal budgetIdleExpense,
                   BigDecimal budgetBaseExpense, BigDecimal budgetRdExpense,
                   BigDecimal budgetAssetImpairment, BigDecimal budgetCreditImpairment,
                   BigDecimal budgetOtherIncome) {
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
        this.budgetVolume = value(budgetVolume);
        this.budgetRevenue = value(budgetRevenue);
        this.budgetSalesCost = value(budgetSalesCost);
        this.budgetIdleExpense = value(budgetIdleExpense);
        this.budgetBaseExpense = value(budgetBaseExpense);
        this.budgetRdExpense = value(budgetRdExpense);
        this.budgetAssetImpairment = value(budgetAssetImpairment);
        this.budgetCreditImpairment = value(budgetCreditImpairment);
        this.budgetOtherIncome = value(budgetOtherIncome);
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
    public BigDecimal getBudgetVolume() { return budgetVolume; }
    public BigDecimal getBudgetRevenue() { return budgetRevenue; }
    public BigDecimal getBudgetSalesCost() { return budgetSalesCost; }
    public BigDecimal getBudgetIdleExpense() { return budgetIdleExpense; }
    public BigDecimal getBudgetBaseExpense() { return budgetBaseExpense; }
    public BigDecimal getBudgetRdExpense() { return budgetRdExpense; }
    public BigDecimal getBudgetAssetImpairment() { return budgetAssetImpairment; }
    public BigDecimal getBudgetCreditImpairment() { return budgetCreditImpairment; }
    public BigDecimal getBudgetOtherIncome() { return budgetOtherIncome; }

    private BigDecimal value(BigDecimal number) {
        return number == null ? BigDecimal.ZERO : number;
    }
}
