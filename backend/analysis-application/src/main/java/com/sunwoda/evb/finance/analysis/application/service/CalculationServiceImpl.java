package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

@Service
public class CalculationServiceImpl implements CalculationService {
    private final AnalysisBatchRepository batchRepository;
    private final PnlFactProvider factProvider;
    private final UserScopeRepository userScopeRepository;
    private final Map<String, CalculationResult> results = new LinkedHashMap<String, CalculationResult>();
    private final CalculationResultRepository resultRepository;

    @Autowired
    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider,
                                  UserScopeRepository userScopeRepository,
                                  CalculationResultRepository resultRepository) {
        this.batchRepository = batchRepository;
        this.factProvider = factProvider;
        this.userScopeRepository = userScopeRepository;
        this.resultRepository = resultRepository;
    }

    /** 保留给领域单元测试和无Spring场景的轻量构造器。 */
    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider,
                                  UserScopeRepository userScopeRepository) {
        this.batchRepository = batchRepository;
        this.factProvider = factProvider;
        this.userScopeRepository = userScopeRepository;
        this.resultRepository = null;
    }

    @Override
    public synchronized CalculationResult calculate(String batchNo) {
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        if (batch.getStatus() == BatchStatus.CONFIRMED || batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new IllegalStateException("已确认或已发布批次不可直接重算");
        }
        if (batch.getStatus() != BatchStatus.READY_FOR_CALCULATION
                && batch.getStatus() != BatchStatus.CALCULATED) {
            throw new IllegalStateException("批次尚未完成校验，不能开始计算: " + batch.getStatus());
        }
        batch.moveTo(BatchStatus.CALCULATING);
        batchRepository.save(batch);
        try {
            Map<String, MutablePnl> grouped = new LinkedHashMap<String, MutablePnl>();
            for (PnlFact fact : factProvider.load(batch)) {
                MutablePnl pnl = grouped.get(fact.getScopeCode());
                if (pnl == null) {
                    pnl = new MutablePnl();
                    grouped.put(fact.getScopeCode(), pnl);
                }
                pnl.add(fact);
            }
            List<PnlResult> pnlResults = new ArrayList<PnlResult>();
            for (Map.Entry<String, MutablePnl> entry : grouped.entrySet()) {
                pnlResults.add(new PnlResult(entry.getKey(), entry.getValue().toLines()));
            }
            CalculationResult result = new CalculationResult(batchNo, batch.getPerspective(), pnlResults);
            saveResult(result);
            batch.moveTo(BatchStatus.CALCULATED);
            batchRepository.save(batch);
            return result;
        } catch (RuntimeException ex) {
            batch.moveTo(BatchStatus.FAILED);
            batchRepository.save(batch);
            throw ex;
        }
    }

    @Override
    public synchronized CalculationResult get(String batchNo) {
        CalculationResult result = resultRepository == null
                ? results.get(batchNo)
                : resultRepository.findByBatchNo(batchNo).orElse(null);
        if (result == null) throw new IllegalArgumentException("批次尚未完成计算: " + batchNo);
        return result;
    }

    @Override
    public synchronized CalculationResult getForUser(String batchNo, String userId) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        CalculationResult result = get(batchNo);
        java.util.List<com.sunwoda.evb.finance.analysis.domain.model.UserScope> scopes =
                userScopeRepository.findByUserAndPerspective(userId.trim(), result.getPerspective());
        java.util.Set<String> allowed = new HashSet<String>();
        boolean canReviewDraft = false;
        for (com.sunwoda.evb.finance.analysis.domain.model.UserScope scope : scopes) {
            allowed.add(scope.getScopeCode());
            String role = scope.getRoleCode().toUpperCase(java.util.Locale.ROOT);
            canReviewDraft = canReviewDraft || role.contains("FINANCE") || role.contains("ADMIN");
        }
        if (result.getStatus() != CalculationResultStatus.PUBLISHED && !canReviewDraft) {
            throw new IllegalStateException("当前结果尚未发布，仅财经角色可查看草稿");
        }
        return result.filterScopes(allowed);
    }

    @Override
    public synchronized CalculationResult confirm(String batchNo, String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("确认人不能为空");
        }
        CalculationResult current = get(batchNo);
        CalculationResult confirmed = current.confirm(operator.trim());
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        batch.moveTo(BatchStatus.CONFIRMED);
        batchRepository.save(batch);
        saveResult(confirmed);
        return confirmed;
    }

    @Override
    public synchronized CalculationResult publish(String batchNo, String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("发布人不能为空");
        }
        CalculationResult current = get(batchNo);
        CalculationResult published = current.publish(operator.trim());
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        batch.moveTo(BatchStatus.PUBLISHED);
        batchRepository.save(batch);
        saveResult(published);
        return published;
    }

    private void saveResult(CalculationResult result) {
        if (resultRepository == null) results.put(result.getBatchNo(), result);
        else resultRepository.save(result);
    }

    private static class MutablePnl {
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal salesCost = BigDecimal.ZERO;
        private BigDecimal idleExpense = BigDecimal.ZERO;
        private BigDecimal baseExpense = BigDecimal.ZERO;
        private BigDecimal rdExpense = BigDecimal.ZERO;
        private BigDecimal assetImpairment = BigDecimal.ZERO;
        private BigDecimal creditImpairment = BigDecimal.ZERO;
        private BigDecimal otherIncome = BigDecimal.ZERO;
        private BigDecimal budgetVolume = BigDecimal.ZERO;
        private BigDecimal budgetRevenue = BigDecimal.ZERO;
        private BigDecimal budgetSalesCost = BigDecimal.ZERO;
        private BigDecimal budgetIdleExpense = BigDecimal.ZERO;
        private BigDecimal budgetBaseExpense = BigDecimal.ZERO;
        private BigDecimal budgetRdExpense = BigDecimal.ZERO;
        private BigDecimal budgetAssetImpairment = BigDecimal.ZERO;
        private BigDecimal budgetCreditImpairment = BigDecimal.ZERO;
        private BigDecimal budgetOtherIncome = BigDecimal.ZERO;
        private BigDecimal materialCost = BigDecimal.ZERO;
        private BigDecimal laborCost = BigDecimal.ZERO;
        private BigDecimal outsourcingCost = BigDecimal.ZERO;
        private BigDecimal variableManufacturingCost = BigDecimal.ZERO;
        private BigDecimal fixedManufacturingCost = BigDecimal.ZERO;
        private BigDecimal budgetMaterialCost = BigDecimal.ZERO;
        private BigDecimal budgetLaborCost = BigDecimal.ZERO;
        private BigDecimal budgetOutsourcingCost = BigDecimal.ZERO;
        private BigDecimal budgetVariableManufacturingCost = BigDecimal.ZERO;
        private BigDecimal budgetFixedManufacturingCost = BigDecimal.ZERO;

        void add(PnlFact fact) {
            volume = volume.add(fact.getVolume());
            revenue = revenue.add(fact.getRevenue());
            salesCost = salesCost.add(fact.getSalesCost());
            idleExpense = idleExpense.add(fact.getIdleExpense());
            baseExpense = baseExpense.add(fact.getBaseExpense());
            rdExpense = rdExpense.add(fact.getRdExpense());
            assetImpairment = assetImpairment.add(fact.getAssetImpairment());
            creditImpairment = creditImpairment.add(fact.getCreditImpairment());
            otherIncome = otherIncome.add(fact.getOtherIncome());
            budgetVolume = budgetVolume.add(fact.getBudgetVolume());
            budgetRevenue = budgetRevenue.add(fact.getBudgetRevenue());
            budgetSalesCost = budgetSalesCost.add(fact.getBudgetSalesCost());
            budgetIdleExpense = budgetIdleExpense.add(fact.getBudgetIdleExpense());
            budgetBaseExpense = budgetBaseExpense.add(fact.getBudgetBaseExpense());
            budgetRdExpense = budgetRdExpense.add(fact.getBudgetRdExpense());
            budgetAssetImpairment = budgetAssetImpairment.add(fact.getBudgetAssetImpairment());
            budgetCreditImpairment = budgetCreditImpairment.add(fact.getBudgetCreditImpairment());
            budgetOtherIncome = budgetOtherIncome.add(fact.getBudgetOtherIncome());
            materialCost = materialCost.add(fact.getMaterialCost());
            laborCost = laborCost.add(fact.getLaborCost());
            outsourcingCost = outsourcingCost.add(fact.getOutsourcingCost());
            variableManufacturingCost = variableManufacturingCost.add(fact.getVariableManufacturingCost());
            fixedManufacturingCost = fixedManufacturingCost.add(fact.getFixedManufacturingCost());
            budgetMaterialCost = budgetMaterialCost.add(fact.getBudgetMaterialCost());
            budgetLaborCost = budgetLaborCost.add(fact.getBudgetLaborCost());
            budgetOutsourcingCost = budgetOutsourcingCost.add(fact.getBudgetOutsourcingCost());
            budgetVariableManufacturingCost = budgetVariableManufacturingCost.add(fact.getBudgetVariableManufacturingCost());
            budgetFixedManufacturingCost = budgetFixedManufacturingCost.add(fact.getBudgetFixedManufacturingCost());
        }

        Map<String, BigDecimal> toLines() {
            BigDecimal grossProfit = revenue.subtract(salesCost);
            BigDecimal otherLoss = assetImpairment.add(creditImpairment);
            BigDecimal netProfit = grossProfit.subtract(idleExpense).subtract(baseExpense)
                    .subtract(rdExpense).subtract(otherLoss).add(otherIncome);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : netProfit.divide(revenue, 8, RoundingMode.HALF_UP);
            BigDecimal budgetGrossProfit = budgetRevenue.subtract(budgetSalesCost);
            BigDecimal budgetOtherLoss = budgetAssetImpairment.add(budgetCreditImpairment);
            BigDecimal budgetNetProfit = budgetGrossProfit.subtract(budgetIdleExpense)
                    .subtract(budgetBaseExpense).subtract(budgetRdExpense)
                    .subtract(budgetOtherLoss).add(budgetOtherIncome);
            BigDecimal budgetProfitRate = budgetRevenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : budgetNetProfit.divide(budgetRevenue, 8, RoundingMode.HALF_UP);
            Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
            lines.put("VOLUME", volume);
            lines.put("REVENUE", revenue);
            lines.put("SALES_COST", salesCost);
            lines.put("GROSS_PROFIT", grossProfit);
            lines.put("IDLE_EXPENSE", idleExpense);
            lines.put("BASE_EXPENSE", baseExpense);
            lines.put("RD_EXPENSE", rdExpense);
            lines.put("ASSET_IMPAIRMENT", assetImpairment);
            lines.put("CREDIT_IMPAIRMENT", creditImpairment);
            lines.put("OTHER_INCOME", otherIncome);
            lines.put("NET_PROFIT", netProfit);
            lines.put("PROFIT_RATE", profitRate);
            lines.put("BUDGET_VOLUME", budgetVolume);
            lines.put("BUDGET_REVENUE", budgetRevenue);
            lines.put("BUDGET_SALES_COST", budgetSalesCost);
            lines.put("BUDGET_GROSS_PROFIT", budgetGrossProfit);
            lines.put("BUDGET_IDLE_EXPENSE", budgetIdleExpense);
            lines.put("BUDGET_BASE_EXPENSE", budgetBaseExpense);
            lines.put("BUDGET_RD_EXPENSE", budgetRdExpense);
            lines.put("BUDGET_ASSET_IMPAIRMENT", budgetAssetImpairment);
            lines.put("BUDGET_CREDIT_IMPAIRMENT", budgetCreditImpairment);
            lines.put("BUDGET_OTHER_INCOME", budgetOtherIncome);
            lines.put("MATERIAL_COST", materialCost);
            lines.put("LABOR_COST", laborCost);
            lines.put("OUTSOURCING_COST", outsourcingCost);
            lines.put("VARIABLE_MFG_COST", variableManufacturingCost);
            lines.put("FIXED_MFG_COST", fixedManufacturingCost);
            lines.put("BUDGET_MATERIAL_COST", budgetMaterialCost);
            lines.put("BUDGET_LABOR_COST", budgetLaborCost);
            lines.put("BUDGET_OUTSOURCING_COST", budgetOutsourcingCost);
            lines.put("BUDGET_VARIABLE_MFG_COST", budgetVariableManufacturingCost);
            lines.put("BUDGET_FIXED_MFG_COST", budgetFixedManufacturingCost);
            lines.put("BUDGET_NET_PROFIT", budgetNetProfit);
            lines.put("BUDGET_PROFIT_RATE", budgetProfitRate);
            lines.put("GAP_VOLUME", volume.subtract(budgetVolume));
            lines.put("GAP_REVENUE", revenue.subtract(budgetRevenue));
            lines.put("GAP_SALES_COST", salesCost.subtract(budgetSalesCost));
            lines.put("GAP_GROSS_PROFIT", grossProfit.subtract(budgetGrossProfit));
            lines.put("GAP_NET_PROFIT", netProfit.subtract(budgetNetProfit));
            lines.put("GAP_PROFIT_RATE", profitRate.subtract(budgetProfitRate));
            lines.put("GAP_MATERIAL_COST", materialCost.subtract(budgetMaterialCost));
            lines.put("GAP_LABOR_COST", laborCost.subtract(budgetLaborCost));
            lines.put("GAP_OUTSOURCING_COST", outsourcingCost.subtract(budgetOutsourcingCost));
            lines.put("GAP_VARIABLE_MFG_COST", variableManufacturingCost.subtract(budgetVariableManufacturingCost));
            lines.put("GAP_FIXED_MFG_COST", fixedManufacturingCost.subtract(budgetFixedManufacturingCost));
            return lines;
        }
    }
}
