package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一期财务样表 2.3/2.4 的价、量、成本、结构计算。 */
final class SamplePvmCalculator {
    private static final int DIVISION_SCALE = 18;

    Map<String, BigDecimal> calculate(PnlResult pnl, List<PnlFact> facts) {
        Map<String, Item> byModel = new LinkedHashMap<String, Item>();
        if (facts != null) {
            for (PnlFact fact : facts) {
                if (!pnl.getScopeCode().equals(fact.getScopeCode())) continue;
                String model = fact.getProductCode() == null ? pnl.getScopeCode() : fact.getProductCode();
                Item item = byModel.computeIfAbsent(model, ignored -> new Item());
                item.add(fact);
            }
        }
        if (byModel.isEmpty()) {
            Item item = new Item();
            item.actualVolume = value(pnl, "VOLUME");
            item.budgetVolume = value(pnl, "BUDGET_VOLUME");
            item.actualRevenue = value(pnl, "REVENUE");
            item.budgetRevenue = value(pnl, "BUDGET_REVENUE");
            item.actualCost = value(pnl, "SALES_COST");
            item.budgetCost = value(pnl, "BUDGET_SALES_COST");
            item.actualMaterial = value(pnl, "MATERIAL_COST");
            item.budgetMaterial = value(pnl, "BUDGET_MATERIAL_COST");
            item.actualLabor = value(pnl, "LABOR_COST");
            item.budgetLabor = value(pnl, "BUDGET_LABOR_COST");
            item.actualOutsourcing = value(pnl, "OUTSOURCING_COST");
            item.budgetOutsourcing = value(pnl, "BUDGET_OUTSOURCING_COST");
            item.actualVariable = value(pnl, "VARIABLE_MFG_COST");
            item.budgetVariable = value(pnl, "BUDGET_VARIABLE_MFG_COST");
            item.actualFixed = value(pnl, "FIXED_MFG_COST");
            item.budgetFixed = value(pnl, "BUDGET_FIXED_MFG_COST");
            byModel.put(pnl.getScopeCode(), item);
        }

        List<Item> items = new ArrayList<Item>(byModel.values());
        BigDecimal totalActualVolume = sum(items, true);
        BigDecimal totalBudgetVolume = sum(items, false);
        BigDecimal totalBudgetMargin = BigDecimal.ZERO;
        for (Item item : items) {
            totalBudgetMargin = totalBudgetMargin.add(item.budgetRevenue.subtract(item.budgetCost));
        }
        BigDecimal averageBudgetMargin = divide(totalBudgetMargin, totalBudgetVolume);
        Map<String, BigDecimal> impacts = new LinkedHashMap<String, BigDecimal>();
        for (String key : new String[]{"REVENUE_PRICE_IMPACT", "REVENUE_VOLUME_IMPACT",
                "REVENUE_STRUCTURE_IMPACT", "PROFIT_PRICE_IMPACT", "PROFIT_VOLUME_IMPACT",
                "PROFIT_COST_IMPACT", "PROFIT_STRUCTURE_IMPACT", "PROFIT_MATERIAL_IMPACT",
                "PROFIT_MATERIAL_LOSS_IMPACT", "PROFIT_LABOR_IMPACT",
                "PROFIT_OUTSOURCING_IMPACT", "PROFIT_VARIABLE_MFG_IMPACT",
                "PROFIT_FIXED_MFG_IMPACT"}) {
            impacts.put(key, BigDecimal.ZERO);
        }
        for (Item item : items) {
            BigDecimal actualMargin = item.actualRevenue.subtract(item.actualCost);
            BigDecimal budgetMargin = item.budgetRevenue.subtract(item.budgetCost);
            if (item.actualVolume.signum() == 0 || item.budgetVolume.signum() == 0) {
                add(impacts, "REVENUE_STRUCTURE_IMPACT", item.actualRevenue.subtract(item.budgetRevenue));
                add(impacts, "PROFIT_STRUCTURE_IMPACT", actualMargin.subtract(budgetMargin));
                continue;
            }
            BigDecimal actualPrice = divide(item.actualRevenue, item.actualVolume);
            BigDecimal budgetPrice = divide(item.budgetRevenue, item.budgetVolume);
            BigDecimal actualUnitCost = divide(item.actualCost, item.actualVolume);
            BigDecimal budgetUnitCost = divide(item.budgetCost, item.budgetVolume);
            BigDecimal budgetUnitMargin = budgetPrice.subtract(budgetUnitCost);
            BigDecimal budgetShare = divide(item.budgetVolume, totalBudgetVolume);
            BigDecimal standardVolume = totalActualVolume.multiply(budgetShare);
            BigDecimal price = actualPrice.subtract(budgetPrice).multiply(item.actualVolume);
            add(impacts, "REVENUE_PRICE_IMPACT", price);
            add(impacts, "REVENUE_VOLUME_IMPACT",
                    standardVolume.subtract(item.budgetVolume).multiply(budgetPrice));
            add(impacts, "REVENUE_STRUCTURE_IMPACT",
                    item.actualVolume.subtract(standardVolume).multiply(budgetPrice));
            add(impacts, "PROFIT_PRICE_IMPACT", price);
            add(impacts, "PROFIT_VOLUME_IMPACT",
                    item.actualVolume.subtract(item.budgetVolume).multiply(averageBudgetMargin));
            add(impacts, "PROFIT_COST_IMPACT",
                    budgetUnitCost.subtract(actualUnitCost).multiply(item.actualVolume));
            add(impacts, "PROFIT_STRUCTURE_IMPACT",
                    item.actualVolume.subtract(standardVolume)
                            .multiply(budgetUnitMargin.subtract(averageBudgetMargin)));
            addUnitCost(impacts, "PROFIT_MATERIAL_IMPACT",
                    item.actualMaterial.subtract(item.actualMaterialLoss),
                    item.budgetMaterial.subtract(item.budgetMaterialLoss), item);
            addUnitCost(impacts, "PROFIT_MATERIAL_LOSS_IMPACT",
                    item.actualMaterialLoss, item.budgetMaterialLoss, item);
            addUnitCost(impacts, "PROFIT_LABOR_IMPACT", item.actualLabor, item.budgetLabor, item);
            addUnitCost(impacts, "PROFIT_OUTSOURCING_IMPACT",
                    item.actualOutsourcing, item.budgetOutsourcing, item);
            addUnitCost(impacts, "PROFIT_VARIABLE_MFG_IMPACT",
                    item.actualVariable, item.budgetVariable, item);
            addUnitCost(impacts, "PROFIT_FIXED_MFG_IMPACT", item.actualFixed, item.budgetFixed, item);
        }
        return impacts;
    }

    private void addUnitCost(Map<String, BigDecimal> impacts, String key,
                             BigDecimal actualAmount, BigDecimal budgetAmount, Item item) {
        add(impacts, key, divide(budgetAmount, item.budgetVolume)
                .subtract(divide(actualAmount, item.actualVolume)).multiply(item.actualVolume));
    }

    private static void add(Map<String, BigDecimal> impacts, String key, BigDecimal amount) {
        impacts.put(key, impacts.get(key).add(amount));
    }

    private static BigDecimal sum(List<Item> items, boolean actual) {
        BigDecimal total = BigDecimal.ZERO;
        for (Item item : items) total = total.add(actual ? item.actualVolume : item.budgetVolume);
        return total;
    }

    private static BigDecimal divide(BigDecimal amount, BigDecimal denominator) {
        return denominator.signum() == 0 ? BigDecimal.ZERO
                : amount.divide(denominator, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(PnlResult pnl, String key) {
        BigDecimal amount = pnl.getLines().get(key);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static final class Item {
        private BigDecimal actualVolume = BigDecimal.ZERO, budgetVolume = BigDecimal.ZERO;
        private BigDecimal actualRevenue = BigDecimal.ZERO, budgetRevenue = BigDecimal.ZERO;
        private BigDecimal actualCost = BigDecimal.ZERO, budgetCost = BigDecimal.ZERO;
        private BigDecimal actualMaterial = BigDecimal.ZERO, budgetMaterial = BigDecimal.ZERO;
        private BigDecimal actualMaterialLoss = BigDecimal.ZERO, budgetMaterialLoss = BigDecimal.ZERO;
        private BigDecimal actualLabor = BigDecimal.ZERO, budgetLabor = BigDecimal.ZERO;
        private BigDecimal actualOutsourcing = BigDecimal.ZERO, budgetOutsourcing = BigDecimal.ZERO;
        private BigDecimal actualVariable = BigDecimal.ZERO, budgetVariable = BigDecimal.ZERO;
        private BigDecimal actualFixed = BigDecimal.ZERO, budgetFixed = BigDecimal.ZERO;

        private void add(PnlFact fact) {
            actualVolume = actualVolume.add(fact.getVolume());
            budgetVolume = budgetVolume.add(fact.getBudgetVolume());
            actualRevenue = actualRevenue.add(fact.getRevenue());
            budgetRevenue = budgetRevenue.add(fact.getBudgetRevenue());
            actualCost = actualCost.add(fact.getSalesCost());
            budgetCost = budgetCost.add(fact.getBudgetSalesCost());
            actualMaterial = actualMaterial.add(fact.getMaterialCost());
            budgetMaterial = budgetMaterial.add(fact.getBudgetMaterialCost());
            actualMaterialLoss = actualMaterialLoss.add(value(fact.getMaterialLoss()));
            budgetMaterialLoss = budgetMaterialLoss.add(value(fact.getBudgetMaterialLoss()));
            actualLabor = actualLabor.add(fact.getLaborCost());
            budgetLabor = budgetLabor.add(fact.getBudgetLaborCost());
            actualOutsourcing = actualOutsourcing.add(fact.getOutsourcingCost());
            budgetOutsourcing = budgetOutsourcing.add(fact.getBudgetOutsourcingCost());
            actualVariable = actualVariable.add(fact.getVariableManufacturingCost());
            budgetVariable = budgetVariable.add(fact.getBudgetVariableManufacturingCost());
            actualFixed = actualFixed.add(fact.getFixedManufacturingCost());
            budgetFixed = budgetFixed.add(fact.getBudgetFixedManufacturingCost());
        }
    }
}
