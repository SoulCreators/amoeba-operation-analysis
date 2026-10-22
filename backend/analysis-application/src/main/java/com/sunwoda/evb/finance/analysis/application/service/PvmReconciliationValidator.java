package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Reuses the existing PVM reconciliation formulas without depending on a
 * calculation service. This keeps the publish gate independent from the
 * read-side PVM service and avoids a Spring dependency cycle.
 */
final class PvmReconciliationValidator {
    Checks calculate(PnlResult pnl) {
        Map<String, BigDecimal> lines = pnl.getLines();
        BigDecimal budgetVolume = value(lines, "BUDGET_VOLUME");
        BigDecimal actualVolume = value(lines, "VOLUME");
        BigDecimal budgetRevenue = value(lines, "BUDGET_REVENUE");
        BigDecimal actualRevenue = value(lines, "REVENUE");
        BigDecimal budgetCost = value(lines, "BUDGET_SALES_COST");
        BigDecimal actualCost = value(lines, "SALES_COST");
        BigDecimal revenueGap = actualRevenue.subtract(budgetRevenue);
        BigDecimal volumeImpact = actualVolume.subtract(budgetVolume)
                .multiply(unitPrice(budgetRevenue, budgetVolume));
        BigDecimal priceImpact = unitPrice(actualRevenue, actualVolume)
                .subtract(unitPrice(budgetRevenue, budgetVolume))
                .multiply(actualVolume);
        BigDecimal grossProfitGap = value(lines, "GAP_GROSS_PROFIT");
        BigDecimal costGap = actualCost.subtract(budgetCost);
        return new Checks(volumeImpact, priceImpact, revenueGap, costGap, grossProfitGap,
                revenueGap.subtract(volumeImpact).subtract(priceImpact),
                grossProfitGap.subtract(volumeImpact).subtract(priceImpact).add(costGap));
    }

    void validate(CalculationResult calculation) {
        for (PnlResult pnl : calculation.getResults()) {
            Checks checks = calculate(pnl);
            if (!checks.isReconciled()) {
                throw new IllegalStateException("PVM勾稽差异非零，范围=" + pnl.getScopeCode()
                        + ", REVENUE_CHECK_DIFF=" + checks.getRevenueCheckDiff()
                        + ", GROSS_PROFIT_CHECK_DIFF=" + checks.getGrossProfitCheckDiff());
            }
        }
    }

    private BigDecimal value(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal unitPrice(BigDecimal revenue, BigDecimal volume) {
        return volume.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : revenue.divide(volume, 8, RoundingMode.HALF_UP);
    }

    static final class Checks {
        private final BigDecimal volumeImpact;
        private final BigDecimal priceImpact;
        private final BigDecimal revenueGap;
        private final BigDecimal costGap;
        private final BigDecimal grossProfitGap;
        private final BigDecimal revenueCheckDiff;
        private final BigDecimal grossProfitCheckDiff;

        Checks(BigDecimal volumeImpact, BigDecimal priceImpact, BigDecimal revenueGap,
               BigDecimal costGap, BigDecimal grossProfitGap,
               BigDecimal revenueCheckDiff, BigDecimal grossProfitCheckDiff) {
            this.volumeImpact = volumeImpact;
            this.priceImpact = priceImpact;
            this.revenueGap = revenueGap;
            this.costGap = costGap;
            this.grossProfitGap = grossProfitGap;
            this.revenueCheckDiff = revenueCheckDiff;
            this.grossProfitCheckDiff = grossProfitCheckDiff;
        }

        BigDecimal getVolumeImpact() { return volumeImpact; }
        BigDecimal getPriceImpact() { return priceImpact; }
        BigDecimal getRevenueGap() { return revenueGap; }
        BigDecimal getCostGap() { return costGap; }
        BigDecimal getGrossProfitGap() { return grossProfitGap; }
        BigDecimal getRevenueCheckDiff() { return revenueCheckDiff; }
        BigDecimal getGrossProfitCheckDiff() { return grossProfitCheckDiff; }

        boolean isReconciled() {
            return revenueCheckDiff.compareTo(BigDecimal.ZERO) == 0
                    && grossProfitCheckDiff.compareTo(BigDecimal.ZERO) == 0;
        }
    }
}
