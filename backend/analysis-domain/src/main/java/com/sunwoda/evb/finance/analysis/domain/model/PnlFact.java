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
    private final BigDecimal materialCost;
    private final BigDecimal laborCost;
    private final BigDecimal outsourcingCost;
    private final BigDecimal variableManufacturingCost;
    private final BigDecimal fixedManufacturingCost;
    private final BigDecimal budgetMaterialCost;
    private final BigDecimal budgetLaborCost;
    private final BigDecimal budgetOutsourcingCost;
    private final BigDecimal budgetVariableManufacturingCost;
    private final BigDecimal budgetFixedManufacturingCost;

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
        this(scopeCode, volume, revenue, salesCost, idleExpense, baseExpense, rdExpense,
                assetImpairment, creditImpairment, otherIncome, budgetVolume, budgetRevenue,
                budgetSalesCost, budgetIdleExpense, budgetBaseExpense, budgetRdExpense,
                budgetAssetImpairment, budgetCreditImpairment, budgetOtherIncome,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public PnlFact(String scopeCode, BigDecimal volume, BigDecimal revenue,
                   BigDecimal salesCost, BigDecimal idleExpense, BigDecimal baseExpense,
                   BigDecimal rdExpense, BigDecimal assetImpairment,
                   BigDecimal creditImpairment, BigDecimal otherIncome,
                   BigDecimal budgetVolume, BigDecimal budgetRevenue,
                   BigDecimal budgetSalesCost, BigDecimal budgetIdleExpense,
                   BigDecimal budgetBaseExpense, BigDecimal budgetRdExpense,
                   BigDecimal budgetAssetImpairment, BigDecimal budgetCreditImpairment,
                   BigDecimal budgetOtherIncome, BigDecimal materialCost,
                   BigDecimal laborCost, BigDecimal outsourcingCost,
                   BigDecimal variableManufacturingCost, BigDecimal fixedManufacturingCost,
                   BigDecimal budgetMaterialCost, BigDecimal budgetLaborCost,
                   BigDecimal budgetOutsourcingCost, BigDecimal budgetVariableManufacturingCost,
                   BigDecimal budgetFixedManufacturingCost) {
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
        this.materialCost = value(materialCost);
        this.laborCost = value(laborCost);
        this.outsourcingCost = value(outsourcingCost);
        this.variableManufacturingCost = value(variableManufacturingCost);
        this.fixedManufacturingCost = value(fixedManufacturingCost);
        this.budgetMaterialCost = value(budgetMaterialCost);
        this.budgetLaborCost = value(budgetLaborCost);
        this.budgetOutsourcingCost = value(budgetOutsourcingCost);
        this.budgetVariableManufacturingCost = value(budgetVariableManufacturingCost);
        this.budgetFixedManufacturingCost = value(budgetFixedManufacturingCost);
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
    public BigDecimal getMaterialCost() { return materialCost; }
    public BigDecimal getLaborCost() { return laborCost; }
    public BigDecimal getOutsourcingCost() { return outsourcingCost; }
    public BigDecimal getVariableManufacturingCost() { return variableManufacturingCost; }
    public BigDecimal getFixedManufacturingCost() { return fixedManufacturingCost; }
    public BigDecimal getBudgetMaterialCost() { return budgetMaterialCost; }
    public BigDecimal getBudgetLaborCost() { return budgetLaborCost; }
    public BigDecimal getBudgetOutsourcingCost() { return budgetOutsourcingCost; }
    public BigDecimal getBudgetVariableManufacturingCost() { return budgetVariableManufacturingCost; }
    public BigDecimal getBudgetFixedManufacturingCost() { return budgetFixedManufacturingCost; }

    private BigDecimal value(BigDecimal number) {
        return number == null ? BigDecimal.ZERO : number;
    }
}
