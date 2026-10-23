package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRunStore;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CalculationServiceImpl implements CalculationService {
    private final AnalysisBatchRepository batchRepository;
    private final PnlFactProvider factProvider;
    private final PermissionService permissionService;
    private final Map<String, CalculationResult> results = new LinkedHashMap<String, CalculationResult>();
    private final CalculationResultRepository resultRepository;
    private CalculationRunStore runStore;

    @Autowired(required = false)
    public void setRunStore(CalculationRunStore runStore) {
        this.runStore = runStore;
    }

    @Autowired
    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider,
                                  PermissionService permissionService,
                                  CalculationResultRepository resultRepository) {
        this.batchRepository = batchRepository;
        this.factProvider = factProvider;
        this.permissionService = permissionService;
        this.resultRepository = resultRepository;
    }

    /** 保留原有基于 UserScopeRepository 的应用层构造调用。 */
    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider,
                                  UserScopeRepository userScopeRepository,
                                  CalculationResultRepository resultRepository) {
        this(batchRepository, factProvider, new PermissionServiceImpl(userScopeRepository), resultRepository);
    }

    /** 保留给领域单元测试和无Spring场景的轻量构造器。 */
    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider,
                                  UserScopeRepository userScopeRepository) {
        this(batchRepository, factProvider, new PermissionServiceImpl(userScopeRepository), null);
    }

    @Override
    public synchronized CalculationResult calculate(String batchNo, String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("计算人不能为空");
        }
        if (runStore != null) {
            return runStore.execute(batchNo, operator.trim(),
                    versionNo -> calculateInternal(batchNo, operator.trim(), versionNo));
        }
        CalculationResult previous = resultRepository == null ? results.get(batchNo)
                : resultRepository.findByBatchNo(batchNo).orElse(null);
        String versionNo = previous == null ? batchNo + "-R1"
                : batchNo + "-R" + (Integer.parseInt(previous.getVersionNo()
                        .substring(previous.getVersionNo().lastIndexOf("-R") + 2)) + 1);
        return calculateInternal(batchNo, operator.trim(), versionNo);
    }

    private CalculationResult calculateInternal(String batchNo, String operator, String versionNo) {
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        if (batch.getStatus() == BatchStatus.CONFIRMED || batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new IllegalStateException("已确认或已发布批次不可直接重算");
        }
        if (batch.getStatus() != BatchStatus.READY_FOR_CALCULATION
                && batch.getStatus() != BatchStatus.CALCULATED) {
            throw new IllegalStateException("批次尚未完成校验，不能开始计算: " + batch.getStatus());
        }
        List<PnlFact> facts;
        try {
            // 本次修改：先读取批次涉及的 scope，再在任何状态写入前完成范围授权校验。
            facts = factProvider.load(batch);
        } catch (RuntimeException ex) {
            // 本次修改：保留事实读取失败的 FAILED 状态轨迹，同时不把授权拒绝误记为执行失败。
            markCalculationFailed(batch);
            throw ex;
        }
        java.util.Set<String> involvedScopes = new java.util.LinkedHashSet<String>();
        for (PnlFact fact : facts) {
            if (fact != null && fact.getScopeCode() != null && !fact.getScopeCode().trim().isEmpty()) {
                involvedScopes.add(fact.getScopeCode().trim());
            }
        }
        // 本次新增：总部财经最大权限仍只覆盖其授权 scope，ADMIN 只读身份不会命中计算写权限。
        if (!permissionService.canCalculate(operator, batch.getPerspective(), involvedScopes)) {
            throw new IllegalStateException("当前用户无批次涉及范围的计算权限");
        }
        batch.moveTo(BatchStatus.CALCULATING);
        batchRepository.save(batch);
        try {
            Map<String, MutablePnl> grouped = new LinkedHashMap<String, MutablePnl>();
            for (PnlFact fact : facts) {
                MutablePnl pnl = grouped.get(fact.getScopeCode());
                if (pnl == null) {
                    pnl = new MutablePnl();
                    grouped.put(fact.getScopeCode(), pnl);
                }
                pnl.add(fact);
            }
            List<PnlResult> pnlResults = new ArrayList<PnlResult>();
            for (Map.Entry<String, MutablePnl> entry : grouped.entrySet()) {
                pnlResults.add(new PnlResult(entry.getKey(), entry.getValue().toLines(batch.getPerspective())));
            }
            CalculationResult result = new CalculationResult(batchNo, batch.getPerspective(), pnlResults,
                    versionNo, CalculationResultStatus.CALCULATED, null, null,
                    java.time.LocalDateTime.now(), null, null, facts);
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

    private void markCalculationFailed(AnalysisBatch batch) {
        // 本次新增：READY/CALCULATED 不能直接转 FAILED，先留下 CALCULATING 再转 FAILED。
        batch.moveTo(BatchStatus.CALCULATING);
        batchRepository.save(batch);
        batch.moveTo(BatchStatus.FAILED);
        batchRepository.save(batch);
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
        String normalizedUser = userId.trim();
        CalculationResult result;
        if (resultRepository == null) {
            result = results.get(batchNo);
        } else {
            result = resultRepository.findByBatchNoForUser(batchNo, normalizedUser).orElse(null);
            if (result == null) {
                result = resultRepository.findMetadataByBatchNo(batchNo).orElse(null);
            }
        }
        if (result == null) throw new IllegalArgumentException("批次尚未完成计算: " + batchNo);
        return filterForUser(result, normalizedUser);
    }

    @Override
    public synchronized CalculationResult getCumulativeForUser(String batchNo, String userId) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        String normalizedUser = userId.trim();
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        // 累计 = 当年 1 月至目标批次所在月，逐月加载事实后统一汇总；结果只读派生，不落库不覆盖。
        String year = batch.getPeriod().substring(0, 4);
        List<AnalysisBatch> batches = batchRepository.findByPerspectiveAndPeriodRange(
                batch.getPerspective(), year + "-01", batch.getPeriod());
        List<PnlFact> facts = new ArrayList<PnlFact>();
        for (AnalysisBatch monthly : batches) {
            try {
                facts.addAll(factProvider.load(monthly));
            } catch (RuntimeException ex) {
                throw new IllegalStateException("累计计算缺少可用期间数据: " + monthly.getPeriod()
                        + "（" + ex.getMessage() + "）", ex);
            }
        }
        Map<String, MutablePnl> grouped = new LinkedHashMap<String, MutablePnl>();
        for (PnlFact fact : facts) {
            MutablePnl pnl = grouped.get(fact.getScopeCode());
            if (pnl == null) {
                pnl = new MutablePnl();
                grouped.put(fact.getScopeCode(), pnl);
            }
            pnl.add(fact);
        }
        List<PnlResult> pnlResults = new ArrayList<PnlResult>();
        for (Map.Entry<String, MutablePnl> entry : grouped.entrySet()) {
            pnlResults.add(new PnlResult(entry.getKey(), entry.getValue().toLines(batch.getPerspective())));
        }
        // 继承目标批次的版本号与状态，仅用于范围可见性；累计行本身不落库。
        String versionNo = batchNo + "-YTD";
        CalculationResultStatus status = CalculationResultStatus.CALCULATED;
        if (resultRepository != null) {
            CalculationResult current = resultRepository.findByBatchNo(batchNo).orElse(null);
            if (current != null) {
                versionNo = current.getVersionNo();
                status = current.getStatus();
            }
        }
        CalculationResult result = new CalculationResult(batchNo, batch.getPerspective(), pnlResults,
                versionNo, status, null, null,
                java.time.LocalDateTime.now(), null, null, facts);
        return filterForUser(result, normalizedUser);
    }

    @Override
    public synchronized CalculationResult getVersionForUser(String batchNo, String versionNo, String userId) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        if (versionNo == null || versionNo.trim().isEmpty()) throw new IllegalArgumentException("版本不能为空");
        if (resultRepository == null) throw new IllegalStateException("当前存储不支持历史结果");
        CalculationResult result = resultRepository.findVersionForUser(batchNo, versionNo.trim(), userId.trim())
                .orElseThrow(() -> new IllegalStateException("版本不存在或无范围权限"));
        if (!permissionService.canViewHistory(userId.trim(), result.getPerspective())) {
            throw new IllegalStateException("当前用户无历史版本查看权限");
        }
        return filterForUser(result, userId.trim());
    }

    private CalculationResult filterForUser(CalculationResult result, String normalizedUser) {
        if (!permissionService.canViewResult(normalizedUser, result.getPerspective(), result.getStatus())) {
            if (result.getStatus() != CalculationResultStatus.PUBLISHED) {
                throw new IllegalStateException("当前结果尚未发布，仅财经角色可查看草稿");
            }
            throw new IllegalStateException("当前用户无结果查看权限");
        }
        Set<String> allowed = permissionService.viewableScopes(normalizedUser, result.getPerspective());
        Set<String> detailScopes = permissionService.detailScopes(normalizedUser, result.getPerspective());
        List<PnlResult> visibleResults = new ArrayList<PnlResult>();
        for (PnlResult pnl : result.getResults()) {
            if (containsScope(allowed, pnl.getScopeCode())) visibleResults.add(pnl);
        }
        List<PnlFact> visibleFacts = new ArrayList<PnlFact>();
        for (PnlFact fact : result.getFacts()) {
            if (containsScope(detailScopes, fact.getScopeCode())) visibleFacts.add(fact);
        }
        if (visibleResults.isEmpty() && !result.getResults().isEmpty()) {
            throw new IllegalStateException("当前用户无结果范围查看权限");
        }
        return new CalculationResult(result.getBatchNo(), result.getPerspective(), visibleResults,
                result.getVersionNo(), result.getStatus(), result.getConfirmedBy(), result.getPublishedBy(),
                result.getCalculatedAt(), result.getConfirmedAt(), result.getPublishedAt(), visibleFacts,
                result.getWithdrawnBy(), result.getWithdrawnAt(), result.getWithdrawReason(),
                result.getNotificationTargets());
    }

    private boolean containsScope(Set<String> allowedScopes, String scopeCode) {
        if (allowedScopes == null || allowedScopes.isEmpty() || scopeCode == null) return false;
        String normalized = scopeCode.trim().toUpperCase(Locale.ROOT);
        return allowedScopes.contains("ALL") || allowedScopes.contains(normalized);
    }

    @Override
    @Transactional
    public synchronized CalculationResult confirm(String batchNo, String operator) {
        // 本次修改：确认时由同一数据库事务覆盖批次状态与结果状态，结果保存失败不得提交批次新状态。
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("确认人不能为空");
        }
        if (runStore != null) runStore.lockBatch(batchNo);
        CalculationResult current = get(batchNo);
        // 本次新增：确认人与发布人不要求不同；只校验当前操作者是否具备确认权限。
        if (!permissionService.canConfirm(operator.trim(), current.getPerspective(), resultScopes(current))) {
            throw new IllegalStateException("当前用户无结果确认权限");
        }
        if (runStore != null) runStore.assertPublishable(current);
        CalculationResult confirmed = current.confirm(operator.trim());
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        batch.moveTo(BatchStatus.CONFIRMED);
        batchRepository.save(batch);
        saveResult(confirmed);
        return confirmed;
    }

    @Override
    @Transactional
    public synchronized CalculationResult publish(String batchNo, String operator) {
        // 本次修改：发布时由同一数据库事务覆盖批次状态与结果状态，避免批次已发布而结果仍为旧状态。
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("发布人不能为空");
        }
        if (runStore != null) runStore.lockBatch(batchNo);
        CalculationResult current = get(batchNo);
        // 本次新增：发布权限独立校验，ADMIN 不因全量读取而获得发布写权限。
        if (!permissionService.canPublish(operator.trim(), current.getPerspective(), resultScopes(current))) {
            throw new IllegalStateException("当前用户无结果发布权限");
        }
        if (runStore != null) runStore.assertPublishable(current);
        CalculationResult published = current.publish(operator.trim());
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        batch.moveTo(BatchStatus.PUBLISHED);
        batchRepository.save(batch);
        saveResult(published);
        return published;
    }

    @Override
    @Transactional
    public synchronized CalculationResult withdraw(String batchNo, String reason,
                                                   List<String> notificationTargets, String operator) {
        // 撤回只改结果状态与撤回痕迹，不改金额与指标；已发布结果不可被静默覆盖。
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("撤回人不能为空");
        }
        if (runStore != null) runStore.lockBatch(batchNo);
        CalculationResult current = get(batchNo);
        if (!permissionService.canPublish(operator.trim(), current.getPerspective(), resultScopes(current))) {
            throw new IllegalStateException("当前用户无结果撤回权限");
        }
        CalculationResult withdrawn = current.withdraw(reason, notificationTargets, operator.trim());
        saveResult(withdrawn);
        return withdrawn;
    }

    private void saveResult(CalculationResult result) {
        if (resultRepository == null) results.put(result.getBatchNo(), result);
        else resultRepository.save(result);
    }

    private Set<String> resultScopes(CalculationResult result) {
        Set<String> scopes = new java.util.LinkedHashSet<String>();
        if (result == null || result.getResults() == null) return scopes;
        for (PnlResult pnlResult : result.getResults()) {
            if (pnlResult != null && pnlResult.getScopeCode() != null
                    && !pnlResult.getScopeCode().trim().isEmpty()) {
                scopes.add(pnlResult.getScopeCode().trim());
            }
        }
        return scopes;
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
        private BigDecimal operatingExpense = BigDecimal.ZERO;
        private BigDecimal salesExpense = BigDecimal.ZERO;
        private BigDecimal adminExpense = BigDecimal.ZERO;
        private BigDecimal budgetOperatingExpense = BigDecimal.ZERO;
        private BigDecimal budgetSalesExpense = BigDecimal.ZERO;
        private BigDecimal budgetAdminExpense = BigDecimal.ZERO;

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
            operatingExpense = operatingExpense.add(fact.getOperatingExpense());
            salesExpense = salesExpense.add(fact.getSalesExpense());
            adminExpense = adminExpense.add(fact.getAdminExpense());
            budgetOperatingExpense = budgetOperatingExpense.add(fact.getBudgetOperatingExpense());
            budgetSalesExpense = budgetSalesExpense.add(fact.getBudgetSalesExpense());
            budgetAdminExpense = budgetAdminExpense.add(fact.getBudgetAdminExpense());
        }

        Map<String, BigDecimal> toLines(AnalysisPerspective perspective) {
            BigDecimal grossProfit = revenue.subtract(salesCost);
            BigDecimal pnlGrossProfit = grossProfit.subtract(idleExpense);
            BigDecimal projectProfit = pnlGrossProfit.subtract(baseExpense);
            BigDecimal otherLoss = assetImpairment.add(creditImpairment);
            BigDecimal netProfit = netProfit(perspective, projectProfit, otherLoss);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : netProfit.divide(revenue, 8, RoundingMode.HALF_UP);
            BigDecimal budgetGrossProfit = budgetRevenue.subtract(budgetSalesCost);
            BigDecimal budgetPnlGrossProfit = budgetGrossProfit.subtract(budgetIdleExpense);
            BigDecimal budgetProjectProfit = budgetPnlGrossProfit.subtract(budgetBaseExpense);
            BigDecimal budgetOtherLoss = budgetAssetImpairment.add(budgetCreditImpairment);
            BigDecimal budgetNetProfit = budgetNetProfit(perspective, budgetProjectProfit, budgetOtherLoss);
            BigDecimal budgetProfitRate = budgetRevenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : budgetNetProfit.divide(budgetRevenue, 8, RoundingMode.HALF_UP);
            Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
            lines.put("VOLUME", volume);
            lines.put("REVENUE", revenue);
            lines.put("SALES_COST", salesCost);
            lines.put("GROSS_PROFIT", grossProfit);
            lines.put("PRODUCT_GROSS_PROFIT", grossProfit);
            lines.put("PNL_GROSS_PROFIT", pnlGrossProfit);
            lines.put("PROJECT_PROFIT", projectProfit);
            lines.put("IDLE_EXPENSE", idleExpense);
            lines.put("BASE_EXPENSE", baseExpense);
            lines.put("RD_EXPENSE", rdExpense);
            lines.put("ASSET_IMPAIRMENT", assetImpairment);
            lines.put("CREDIT_IMPAIRMENT", creditImpairment);
            lines.put("OTHER_INCOME", otherIncome);
            lines.put("OPERATING_EXPENSE", operatingExpense);
            lines.put("SALES_EXPENSE", salesExpense);
            lines.put("ADMIN_EXPENSE", adminExpense);
            lines.put("NET_PROFIT", netProfit);
            lines.put("PROFIT_RATE", profitRate);
            lines.put("BUDGET_VOLUME", budgetVolume);
            lines.put("BUDGET_REVENUE", budgetRevenue);
            lines.put("BUDGET_SALES_COST", budgetSalesCost);
            lines.put("BUDGET_GROSS_PROFIT", budgetGrossProfit);
            lines.put("BUDGET_PRODUCT_GROSS_PROFIT", budgetGrossProfit);
            lines.put("BUDGET_PNL_GROSS_PROFIT", budgetPnlGrossProfit);
            lines.put("BUDGET_PROJECT_PROFIT", budgetProjectProfit);
            lines.put("BUDGET_IDLE_EXPENSE", budgetIdleExpense);
            lines.put("BUDGET_BASE_EXPENSE", budgetBaseExpense);
            lines.put("BUDGET_RD_EXPENSE", budgetRdExpense);
            lines.put("BUDGET_ASSET_IMPAIRMENT", budgetAssetImpairment);
            lines.put("BUDGET_CREDIT_IMPAIRMENT", budgetCreditImpairment);
            lines.put("BUDGET_OTHER_INCOME", budgetOtherIncome);
            lines.put("BUDGET_OPERATING_EXPENSE", budgetOperatingExpense);
            lines.put("BUDGET_SALES_EXPENSE", budgetSalesExpense);
            lines.put("BUDGET_ADMIN_EXPENSE", budgetAdminExpense);
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
            lines.put("GAP_PNL_GROSS_PROFIT", pnlGrossProfit.subtract(budgetPnlGrossProfit));
            lines.put("GAP_PROJECT_PROFIT", projectProfit.subtract(budgetProjectProfit));
            lines.put("GAP_NET_PROFIT", netProfit.subtract(budgetNetProfit));
            lines.put("GAP_PROFIT_RATE", profitRate.subtract(budgetProfitRate));
            lines.put("GAP_MATERIAL_COST", materialCost.subtract(budgetMaterialCost));
            lines.put("GAP_LABOR_COST", laborCost.subtract(budgetLaborCost));
            lines.put("GAP_OUTSOURCING_COST", outsourcingCost.subtract(budgetOutsourcingCost));
            lines.put("GAP_VARIABLE_MFG_COST", variableManufacturingCost.subtract(budgetVariableManufacturingCost));
            lines.put("GAP_FIXED_MFG_COST", fixedManufacturingCost.subtract(budgetFixedManufacturingCost));
            lines.put("GAP_OPERATING_EXPENSE", operatingExpense.subtract(budgetOperatingExpense));
            lines.put("GAP_SALES_EXPENSE", salesExpense.subtract(budgetSalesExpense));
            lines.put("GAP_ADMIN_EXPENSE", adminExpense.subtract(budgetAdminExpense));
            return lines;
        }

        /** 一期三视角独立损益公式（08 §2）：基地扣运营费用并加其他收益，产品线只扣研发与减值，事业部扣销售/管理费用。 */
        private BigDecimal netProfit(AnalysisPerspective perspective, BigDecimal projectProfit,
                                     BigDecimal otherLoss) {
            if (perspective == AnalysisPerspective.BASE) {
                return projectProfit.subtract(rdExpense).subtract(operatingExpense).subtract(otherLoss).add(otherIncome);
            }
            if (perspective == AnalysisPerspective.BUSINESS_UNIT) {
                return projectProfit.subtract(rdExpense).subtract(salesExpense).subtract(adminExpense).subtract(otherLoss);
            }
            return projectProfit.subtract(rdExpense).subtract(otherLoss);
        }

        private BigDecimal budgetNetProfit(AnalysisPerspective perspective, BigDecimal projectProfit,
                                           BigDecimal otherLoss) {
            if (perspective == AnalysisPerspective.BASE) {
                return projectProfit.subtract(budgetRdExpense).subtract(budgetOperatingExpense)
                        .subtract(otherLoss).add(budgetOtherIncome);
            }
            if (perspective == AnalysisPerspective.BUSINESS_UNIT) {
                return projectProfit.subtract(budgetRdExpense).subtract(budgetSalesExpense)
                        .subtract(budgetAdminExpense).subtract(otherLoss);
            }
            return projectProfit.subtract(budgetRdExpense).subtract(otherLoss);
        }
    }
}
