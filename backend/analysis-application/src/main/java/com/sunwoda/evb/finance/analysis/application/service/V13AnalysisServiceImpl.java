package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactDetailProvider;
import com.sunwoda.evb.finance.analysis.application.port.FcVersionProvider;
import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotProvider;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** V1.3 经营分析服务实现。 */
@Service
public class V13AnalysisServiceImpl implements V13AnalysisService {
    private static final String STATUS_CALCULATED = "CALCULATED";
    private static final String STATUS_PENDING_DATA_MODEL = "PENDING_DATA_MODEL";
    private static final String STATUS_PENDING_DEFINITION = "PENDING_DEFINITION";
    private static final String STATUS_CALCULATED_TECHNICAL = "CALCULATED_TECHNICAL";
    private static final String STATUS_PENDING_FC_VERSION = "PENDING_FC_VERSION";
    private static final String STATUS_PENDING_SOURCE_DATA = "PENDING_SOURCE_DATA";

    private static final Map<String, String> METRIC_NAMES;
    private static final List<String> METRICS = Arrays.asList(
            "VOLUME", "REVENUE", "GROSS_PROFIT", "BASE_EXPENSE", "NET_PROFIT", "PROFIT_RATE");

    static {
        Map<String, String> names = new LinkedHashMap<String, String>();
        names.put("VOLUME", "销量");
        names.put("REVENUE", "收入");
        names.put("GROSS_PROFIT", "毛利");
        names.put("BASE_EXPENSE", "基地费用");
        names.put("NET_PROFIT", "净利润");
        names.put("PROFIT_RATE", "净利率");
        METRIC_NAMES = Collections.unmodifiableMap(names);
    }

    private final CalculationService calculationService;
    private final PvmService pvmService;
    private final PnlFactDetailProvider detailProvider;
    private final FcVersionProvider fcVersionProvider;
    private final FcSnapshotProvider fcSnapshotProvider;

    public V13AnalysisServiceImpl(CalculationService calculationService, PvmService pvmService) {
        this(calculationService, pvmService, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public V13AnalysisServiceImpl(CalculationService calculationService, PvmService pvmService,
                                  PnlFactDetailProvider detailProvider, FcVersionProvider fcVersionProvider,
                                  FcSnapshotProvider fcSnapshotProvider) {
        this.calculationService = calculationService;
        this.pvmService = pvmService;
        this.detailProvider = detailProvider;
        this.fcVersionProvider = fcVersionProvider;
        this.fcSnapshotProvider = fcSnapshotProvider;
    }

    @Override
    public Map<String, Object> overview(String batchNo, String userId, String scenario) {
        String normalizedScenario = normalizeScenario(scenario);
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, normalizedScenario);
        if (!"ACT_BGT".equals(normalizedScenario)) {
            return pendingForecast(response, normalizedScenario);
        }
        List<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
        for (String metric : METRICS) {
            metrics.add(metricGap(calculation.getResults(), metric));
        }
        response.put("status", STATUS_CALCULATED);
        response.put("metrics", metrics);
        response.put("top", topImpacts(calculation.getResults(), "GROSS_PROFIT", 5));
        response.put("notes", Collections.singletonList("绝对值 GAP 与 TOP 榜由当前批次 ACT/BGT 结果实时计算"));
        return response;
    }

    @Override
    public Map<String, Object> matrix(String batchNo, String userId, String metricCode) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        String metric = normalizeMetric(metricCode);
        Map<String, Object> response = baseResponse(batchNo, calculation, "ACT_BGT");
        List<PnlFact> detailFacts = calculation.getFacts();
        if ((detailFacts == null || detailFacts.isEmpty()) && detailProvider != null) {
            detailFacts = detailProvider.findForUser(batchNo, calculation.getPerspective(), userId);
        }
        if (calculation.getPerspective() == com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective.BUSINESS_UNIT
                && hasMatrixDimensions(detailFacts)) {
            response.put("status", STATUS_CALCULATED);
            response.put("metricCode", metric);
            response.put("rows", matrixRows(detailFacts, metric));
            response.put("blockers", Collections.emptyList());
            response.put("notes", Collections.singletonList("基地×产品矩阵基于标准事实层维度和统一损益公式实时汇总"));
            return response;
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (PnlResult result : calculation.getResults()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("baseCode", result.getScopeCode());
            row.put("productCode", null);
            row.put("dimensionStatus", STATUS_PENDING_DATA_MODEL);
            row.put("actual", value(result.getLines(), metric));
            row.put("budget", value(result.getLines(), budgetKey(metric)));
            row.put("gap", value(result.getLines(), gapKey(metric)));
            row.put("gapRate", gapRate(value(result.getLines(), gapKey(metric)),
                    value(result.getLines(), budgetKey(metric))));
            rows.add(row);
        }
        response.put("status", STATUS_PENDING_DATA_MODEL);
        response.put("metricCode", metric);
        response.put("rows", rows);
        response.put("blockers", Collections.singletonList("当前 PnlFact 只有 scopeCode，尚未落地基地×产品的明细维度"));
        response.put("notes", Collections.singletonList("已返回当前视角聚合结果；补充 baseCode/productCode 后即可切换为正式交叉矩阵"));
        return response;
    }

    private boolean hasMatrixDimensions(List<PnlFact> facts) {
        if (facts == null || facts.isEmpty()) return false;
        boolean found = false;
        for (PnlFact fact : facts) {
            if (fact.getBaseCode() != null && fact.getProductCode() != null) found = true;
        }
        return found;
    }

    private List<Map<String, Object>> matrixRows(List<PnlFact> facts, String metric) {
        Map<String, MatrixValues> grouped = new LinkedHashMap<String, MatrixValues>();
        for (PnlFact fact : facts) {
            // 研发、运营、减值等无产品归属费用不进入基地×产品经营利润矩阵。
            if (fact.getBaseCode() == null || fact.getProductCode() == null) continue;
            String key = fact.getBaseCode() + "\u001f" + fact.getProductCode();
            MatrixValues values = grouped.get(key);
            if (values == null) {
                values = new MatrixValues();
                grouped.put(key, values);
            }
            values.actual = values.actual.add(metricValue(fact, metric, false));
            values.budget = values.budget.add(metricValue(fact, metric, true));
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, MatrixValues> entry : grouped.entrySet()) {
            int delimiter = entry.getKey().indexOf('\u001f');
            String baseCode = delimiter < 0 ? entry.getKey() : entry.getKey().substring(0, delimiter);
            String productCode = delimiter < 0 ? "" : entry.getKey().substring(delimiter + 1);
            MatrixValues values = entry.getValue();
            BigDecimal gap = values.actual.subtract(values.budget);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("baseCode", baseCode);
            row.put("productCode", productCode);
            row.put("actual", values.actual);
            row.put("budget", values.budget);
            row.put("gap", gap);
            row.put("gapRate", gapRate(gap, values.budget));
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal metricValue(PnlFact fact, String metric, boolean budget) {
        if ("VOLUME".equals(metric)) return budget ? fact.getBudgetVolume() : fact.getVolume();
        if ("REVENUE".equals(metric)) return budget ? fact.getBudgetRevenue() : fact.getRevenue();
        if ("BASE_EXPENSE".equals(metric)) return budget ? fact.getBudgetBaseExpense() : fact.getBaseExpense();
        BigDecimal revenue = budget ? fact.getBudgetRevenue() : fact.getRevenue();
        BigDecimal salesCost = budget ? fact.getBudgetSalesCost() : fact.getSalesCost();
        BigDecimal grossProfit = revenue.subtract(salesCost);
        if ("GROSS_PROFIT".equals(metric)) return grossProfit;
        BigDecimal idle = budget ? fact.getBudgetIdleExpense() : fact.getIdleExpense();
        BigDecimal base = budget ? fact.getBudgetBaseExpense() : fact.getBaseExpense();
        BigDecimal rd = budget ? fact.getBudgetRdExpense() : fact.getRdExpense();
        BigDecimal impairment = (budget ? fact.getBudgetAssetImpairment() : fact.getAssetImpairment())
                .add(budget ? fact.getBudgetCreditImpairment() : fact.getCreditImpairment());
        BigDecimal otherIncome = budget ? fact.getBudgetOtherIncome() : fact.getOtherIncome();
        BigDecimal netProfit = grossProfit.subtract(idle).subtract(base).subtract(rd)
                .subtract(impairment).add(otherIncome);
        if ("NET_PROFIT".equals(metric)) return netProfit;
        if ("PROFIT_RATE".equals(metric)) return ratio(netProfit, revenue);
        return BigDecimal.ZERO;
    }

    private static class MatrixValues {
        private BigDecimal actual = BigDecimal.ZERO;
        private BigDecimal budget = BigDecimal.ZERO;
    }

    @Override
    public Map<String, Object> unitEfficiency(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, "ACT_BGT");
        List<PnlFact> facts = calculation.getFacts();
        if ((facts == null || facts.isEmpty()) && detailProvider != null) {
            facts = detailProvider.findForUser(batchNo, calculation.getPerspective(), userId);
        }
        if (facts == null || facts.isEmpty()) {
            response.put("status", STATUS_PENDING_DEFINITION);
            response.put("rows", Collections.emptyList());
            response.put("blockers", Arrays.asList(
                    "当前批次没有可用事实明细",
                    "当前事实模型没有产出 Wh 字段，无法计算元/Wh"));
            response.put("notes", Collections.singletonList("导入包含线体和 Wh 字段的明细后再计算"));
            return response;
        }
        Map<String, UnitValues> grouped = new LinkedHashMap<String, UnitValues>();
        List<String> blockers = new ArrayList<String>();
        for (PnlFact fact : facts) {
            // 期间费用、闲置费用和减值汇总行没有线体维度，不能参与元/Wh；
            // 它们不应阻断收入成本明细中已有线体数据的单位效率计算。
            if (fact.getLineCode() == null) {
                continue;
            }
            if (fact.getOutputWh() == null || fact.getOutputWh().compareTo(BigDecimal.ZERO) <= 0) {
                blockers.add("线体 " + fact.getLineCode() + " 缺少有效实际产出 Wh");
                continue;
            }
            if (fact.getBudgetOutputWh() == null || fact.getBudgetOutputWh().compareTo(BigDecimal.ZERO) <= 0) {
                blockers.add("线体 " + fact.getLineCode() + " 缺少有效预算产出 Wh");
                continue;
            }
            String key = String.valueOf(fact.getScopeCode()) + "\u001f" + fact.getLineCode();
            UnitValues values = grouped.get(key);
            if (values == null) {
                values = new UnitValues(fact.getScopeCode(), fact.getLineCode());
                grouped.put(key, values);
            }
            values.actualCost = values.actualCost.add(fact.getVariableManufacturingCost())
                    .add(fact.getFixedManufacturingCost());
            values.budgetCost = values.budgetCost.add(fact.getBudgetVariableManufacturingCost())
                    .add(fact.getBudgetFixedManufacturingCost());
            values.actualWh = values.actualWh.add(fact.getOutputWh());
            values.budgetWh = values.budgetWh.add(fact.getBudgetOutputWh());
        }
        if (grouped.isEmpty() && blockers.isEmpty()) {
            blockers.add("当前批次没有带线体编码的收入成本事实");
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (UnitValues values : grouped.values()) {
            BigDecimal actualPerWh = ratio(values.actualCost, values.actualWh);
            BigDecimal budgetPerWh = ratio(values.budgetCost, values.budgetWh);
            BigDecimal gap = actualPerWh.subtract(budgetPerWh);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("scopeCode", values.scopeCode);
            row.put("lineCode", values.lineCode);
            row.put("actualManufacturingCost", values.actualCost);
            row.put("budgetManufacturingCost", values.budgetCost);
            row.put("actualOutputWh", values.actualWh);
            row.put("budgetOutputWh", values.budgetWh);
            row.put("actualPerWh", actualPerWh);
            row.put("budgetPerWh", budgetPerWh);
            row.put("gap", gap);
            row.put("gapRate", gapRate(gap, budgetPerWh));
            rows.add(row);
        }
        response.put("rows", rows);
        response.put("costScope", Arrays.asList("VARIABLE_MFG_COST", "FIXED_MFG_COST"));
        if (blockers.isEmpty() && !rows.isEmpty()) {
            response.put("status", STATUS_CALCULATED_TECHNICAL);
            response.put("blockers", Collections.singletonList("业务仍需确认制造费用范围；当前技术口径为变动制费+固定制费"));
            response.put("notes", Collections.singletonList("元/Wh按线体汇总后重算：变动制费+固定制费 ÷ 产出 Wh；尚未作为正式经营口径发布"));
        } else {
            response.put("status", STATUS_PENDING_DEFINITION);
            response.put("blockers", unique(blockers));
            response.put("notes", Collections.singletonList("补齐线体、实际/预算 Wh 后再进入技术计算；业务口径确认后才能发布正式数值"));
        }
        return response;
    }

    private List<String> unique(List<String> values) {
        return new ArrayList<String>(new java.util.LinkedHashSet<String>(values));
    }

    private static class UnitValues {
        private final String scopeCode;
        private final String lineCode;
        private BigDecimal actualCost = BigDecimal.ZERO;
        private BigDecimal budgetCost = BigDecimal.ZERO;
        private BigDecimal actualWh = BigDecimal.ZERO;
        private BigDecimal budgetWh = BigDecimal.ZERO;

        private UnitValues(String scopeCode, String lineCode) {
            this.scopeCode = scopeCode;
            this.lineCode = lineCode;
        }
    }

    @Override
    public Map<String, Object> forecast(String batchNo, String userId, String scenario, String fcVersion) {
        String normalizedScenario = normalizeScenario(scenario);
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, normalizedScenario);
        if (fcVersionProvider != null && normalizedScenario.contains("FC")) {
            java.util.Optional<com.sunwoda.evb.finance.analysis.domain.model.FcVersion> version =
                    fcVersionProvider.findForBatch(batchNo, calculation.getPerspective(), fcVersion);
            if (version.isPresent()) {
                response.put("fcVersion", version.get().getVersionCode());
                response.put("fcVersionStatus", version.get().getStatus());
                if (fcSnapshotProvider != null) {
                    List<FcSnapshot> snapshots = fcSnapshotProvider.findForBatch(
                            batchNo, calculation.getPerspective(), version.get().getVersionCode(), userId);
                    Map<String, BigDecimal> fcValues = aggregateFc(snapshots);
                    if (fcValues.keySet().containsAll(METRICS)) {
                        response.put("status", STATUS_CALCULATED);
                        List<Map<String, Object>> calculatedRows = forecastRows(
                                calculation.getResults(), fcValues, normalizedScenario);
                        response.put("metrics", calculatedRows);
                        response.put("rows", calculatedRows);
                        response.put("top", Collections.emptyMap());
                        response.put("blockers", Collections.emptyList());
                        response.put("notes", Collections.singletonList("FC值来自指定版本预测快照，按当前用户范围汇总"));
                        return response;
                    }
                }
                response.put("status", STATUS_PENDING_SOURCE_DATA);
                response.put("metrics", Collections.emptyList());
                response.put("top", Collections.emptyMap());
                response.put("blockers", Collections.singletonList("FC版本元数据已找到，但预测快照尚未完整接入"));
                response.put("notes", Collections.singletonList("正式FC版本不会用BGT或历史FC静默替代"));
                return response;
            }
        }
        response.put("fcVersion", fcVersion);
        if (fcVersion == null || fcVersion.trim().isEmpty()) {
            response.put("status", STATUS_PENDING_FC_VERSION);
            response.put("blockers", Collections.singletonList("尚未建立 FC 版本管理和滚动预测数据源"));
        } else {
            response.put("status", STATUS_PENDING_FC_VERSION);
            response.put("blockers", Collections.singletonList("FC 版本已传入，但后端尚未接入预测快照存储"));
        }
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String metric : METRICS) {
            Map<String, Object> row = metricGap(calculation.getResults(), metric);
            row.put("fc", null);
            row.put("fcGap", null);
            rows.add(row);
        }
        response.put("rows", rows);
        response.put("notes", Collections.singletonList("ACT/BGT 已由计算结果提供；FC 接入后复用同一口径返回三口径并列数据"));
        return response;
    }

    private Map<String, BigDecimal> aggregateFc(List<FcSnapshot> snapshots) {
        Map<String, BigDecimal> values = new LinkedHashMap<String, BigDecimal>();
        for (FcSnapshot snapshot : snapshots) {
            if (!METRICS.contains(snapshot.getMetricCode())) continue;
            BigDecimal current = values.get(snapshot.getMetricCode());
            values.put(snapshot.getMetricCode(), (current == null ? BigDecimal.ZERO : current).add(snapshot.getValue()));
        }
        return values;
    }

    private List<Map<String, Object>> forecastRows(List<PnlResult> results,
                                                    Map<String, BigDecimal> fcValues,
                                                    String scenario) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (String metric : METRICS) {
            Map<String, Object> row = metricGap(results, metric);
            BigDecimal actual = (BigDecimal) row.get("actual");
            BigDecimal budget = (BigDecimal) row.get("budget");
            BigDecimal fc = fcValues.get(metric);
            row.put("fc", fc);
            if ("FC_BGT".equals(scenario)) {
                row.put("gap", fc.subtract(budget));
                row.put("gapRate", gapRate(fc.subtract(budget), budget));
                row.put("comparison", "FC_BGT");
            } else {
                row.put("gap", actual.subtract(fc));
                row.put("gapRate", gapRate(actual.subtract(fc), fc));
                row.put("comparison", "ACT_FC");
            }
            row.put("fcGap", actual.subtract(fc));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public Map<String, Object> risk(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, "ACT_BGT");
        List<Map<String, Object>> risks = new ArrayList<Map<String, Object>>();
        for (String metric : Arrays.asList("REVENUE", "GROSS_PROFIT", "NET_PROFIT")) {
            Map<String, Object> gap = metricGap(calculation.getResults(), metric);
            BigDecimal gapValue = (BigDecimal) gap.get("gap");
            if (gapValue.compareTo(BigDecimal.ZERO) < 0) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("metricCode", metric);
                item.put("metricName", METRIC_NAMES.get(metric));
                item.put("level", "OBSERVE");
                item.put("gap", gapValue);
                item.put("message", METRIC_NAMES.get(metric) + "低于预算，请结合业务原因进一步核查");
                item.put("thresholdStatus", STATUS_PENDING_DEFINITION);
                risks.add(item);
            }
        }
        response.put("status", risks.isEmpty() ? STATUS_CALCULATED : "PARTIAL_PENDING_THRESHOLD");
        response.put("risks", risks);
        response.put("blockers", Collections.singletonList("风险等级阈值尚未由预算组确认，当前仅输出负向偏差观察项"));
        response.put("notes", Collections.singletonList("风险页可直接供前端打印；正式红黄阈值配置后再升级为正式预警"));
        return response;
    }

    @Override
    public Map<String, Object> waterfall(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        List<PvmResult> pvm = pvmService.analyze(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, "ACT_BGT");
        Map<String, BigDecimal> totals = new LinkedHashMap<String, BigDecimal>();
        for (String key : Arrays.asList("PROFIT_VOLUME_IMPACT", "PROFIT_PRICE_IMPACT",
                "PROFIT_MATERIAL_IMPACT", "PROFIT_LABOR_IMPACT", "PROFIT_OUTSOURCING_IMPACT",
                "PROFIT_VARIABLE_MFG_IMPACT", "PROFIT_FIXED_MFG_IMPACT")) {
            totals.put(key, BigDecimal.ZERO);
        }
        for (PvmResult item : pvm) {
            for (String key : totals.keySet()) {
                totals.put(key, totals.get(key).add(value(item.getValues(), key)));
            }
        }
        List<Map<String, Object>> factors = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            Map<String, Object> factor = new LinkedHashMap<String, Object>();
            factor.put("code", entry.getKey());
            factor.put("value", entry.getValue());
            factors.add(factor);
        }
        BigDecimal actualVolume = sumLine(calculation.getResults(), "VOLUME");
        BigDecimal budgetVolume = sumLine(calculation.getResults(), "BUDGET_VOLUME");
        BigDecimal actualGrossProfit = sumLine(calculation.getResults(), "GROSS_PROFIT");
        BigDecimal budgetGrossProfit = sumLine(calculation.getResults(), "BUDGET_GROSS_PROFIT");
        if (actualVolume.compareTo(BigDecimal.ZERO) <= 0 || budgetVolume.compareTo(BigDecimal.ZERO) <= 0) {
            response.put("status", STATUS_PENDING_DEFINITION);
            response.put("factors", factors);
            response.put("blockers", Arrays.asList(
                    "实际或预算销量为0，无法计算单位毛利",
                    "单位毛利分母仍需财经确认；当前技术候选分母为 VOLUME"));
            response.put("notes", Collections.singletonList("PVM 因子来自正式计算结果；补齐有效销量后再计算单位毛利瀑布"));
            return response;
        }
        BigDecimal budgetUnitGrossProfit = ratio(budgetGrossProfit, budgetVolume);
        BigDecimal actualUnitGrossProfit = ratio(actualGrossProfit, actualVolume);
        BigDecimal volumeNormalization = ratio(budgetGrossProfit, actualVolume)
                .subtract(ratio(budgetGrossProfit, budgetVolume))
                .add(ratio(totals.get("PROFIT_VOLUME_IMPACT"), actualVolume));
        BigDecimal pricePerUnit = ratio(totals.get("PROFIT_PRICE_IMPACT"), actualVolume);
        BigDecimal materialPerUnit = ratio(totals.get("PROFIT_MATERIAL_IMPACT"), actualVolume);
        BigDecimal laborPerUnit = ratio(totals.get("PROFIT_LABOR_IMPACT"), actualVolume);
        BigDecimal outsourcingPerUnit = ratio(totals.get("PROFIT_OUTSOURCING_IMPACT"), actualVolume);
        BigDecimal variableMfgPerUnit = ratio(totals.get("PROFIT_VARIABLE_MFG_IMPACT"), actualVolume);
        BigDecimal fixedMfgPerUnit = ratio(totals.get("PROFIT_FIXED_MFG_IMPACT"), actualVolume);
        BigDecimal componentSum = volumeNormalization.add(pricePerUnit).add(materialPerUnit)
                .add(laborPerUnit).add(outsourcingPerUnit).add(variableMfgPerUnit).add(fixedMfgPerUnit);
        BigDecimal residualPerUnit = actualUnitGrossProfit.subtract(budgetUnitGrossProfit).subtract(componentSum);
        Map<String, Object> unitGrossProfit = new LinkedHashMap<String, Object>();
        unitGrossProfit.put("unitBasis", "VOLUME");
        unitGrossProfit.put("budget", budgetUnitGrossProfit);
        unitGrossProfit.put("actual", actualUnitGrossProfit);
        unitGrossProfit.put("gap", actualUnitGrossProfit.subtract(budgetUnitGrossProfit));
        unitGrossProfit.put("volume", actualVolume);
        unitGrossProfit.put("budgetVolume", budgetVolume);
        Map<String, Object> unitFactors = new LinkedHashMap<String, Object>();
        unitFactors.put("VOLUME_NORMALIZATION_IMPACT", volumeNormalization);
        unitFactors.put("PRICE_IMPACT_PER_UNIT", pricePerUnit);
        unitFactors.put("MATERIAL_IMPACT_PER_UNIT", materialPerUnit);
        unitFactors.put("LABOR_IMPACT_PER_UNIT", laborPerUnit);
        unitFactors.put("OUTSOURCING_IMPACT_PER_UNIT", outsourcingPerUnit);
        unitFactors.put("VARIABLE_MFG_IMPACT_PER_UNIT", variableMfgPerUnit);
        unitFactors.put("FIXED_MFG_IMPACT_PER_UNIT", fixedMfgPerUnit);
        unitFactors.put("OTHER_COST_RESIDUAL_PER_UNIT", residualPerUnit);
        unitGrossProfit.put("factors", unitFactors);
        unitGrossProfit.put("check", componentSum.add(residualPerUnit)
                .subtract(actualUnitGrossProfit.subtract(budgetUnitGrossProfit)));
        response.put("status", STATUS_CALCULATED_TECHNICAL);
        response.put("factors", factors);
        response.put("unitGrossProfit", unitGrossProfit);
        response.put("blockers", Collections.singletonList("业务仍需确认单位毛利分母；当前技术候选分母为 VOLUME"));
        response.put("notes", Collections.singletonList("单位毛利瀑布由真实 PVM 因子归一化到销量，含其他成本残差并提供勾稽检查；尚未作为正式经营口径发布"));
        return response;
    }

    private BigDecimal sumLine(List<PnlResult> results, String key) {
        BigDecimal total = BigDecimal.ZERO;
        for (PnlResult result : results) total = total.add(value(result.getLines(), key));
        return total;
    }

    @Override
    public Map<String, Object> topic(String batchNo, String userId, String topicCode) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        Map<String, Object> response = baseResponse(batchNo, calculation, "ACT_BGT");
        response.put("topicCode", topicCode == null ? "UNKNOWN" : topicCode.toUpperCase(Locale.ROOT));
        response.put("status", STATUS_PENDING_SOURCE_DATA);
        response.put("rows", Collections.emptyList());
        response.put("blockers", Collections.singletonList("该专题所需明细源数据尚未接入当前事实模型"));
        response.put("notes", Collections.singletonList("保留专题接口，接入库龄、研发提单或运营费用明细后再开放正式查询"));
        return response;
    }

    private Map<String, Object> baseResponse(String batchNo, CalculationResult calculation, String scenario) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("batchNo", batchNo);
        response.put("perspective", calculation.getPerspective().getCode());
        response.put("calculationStatus", calculation.getStatus().name());
        response.put("versionNo", calculation.getVersionNo());
        response.put("scenario", scenario);
        return response;
    }

    private Map<String, Object> pendingForecast(Map<String, Object> response, String scenario) {
        response.put("status", STATUS_PENDING_FC_VERSION);
        response.put("metrics", Collections.emptyList());
        response.put("top", Collections.emptyMap());
        response.put("blockers", Collections.singletonList("当前仅有 ACT/BGT 结果，尚未接入 FC 版本数据"));
        response.put("notes", Collections.singletonList("请先创建 FC 版本并导入滚动预测快照"));
        response.put("scenario", scenario);
        return response;
    }

    private Map<String, Object> metricGap(List<PnlResult> results, String metric) {
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal budget = BigDecimal.ZERO;
        if ("PROFIT_RATE".equals(metric)) {
            BigDecimal actualProfit = BigDecimal.ZERO;
            BigDecimal actualRevenue = BigDecimal.ZERO;
            BigDecimal budgetProfit = BigDecimal.ZERO;
            BigDecimal budgetRevenue = BigDecimal.ZERO;
            for (PnlResult result : results) {
                actualProfit = actualProfit.add(value(result.getLines(), "NET_PROFIT"));
                actualRevenue = actualRevenue.add(value(result.getLines(), "REVENUE"));
                budgetProfit = budgetProfit.add(value(result.getLines(), "BUDGET_NET_PROFIT"));
                budgetRevenue = budgetRevenue.add(value(result.getLines(), "BUDGET_REVENUE"));
            }
            actual = ratio(actualProfit, actualRevenue);
            budget = ratio(budgetProfit, budgetRevenue);
        } else {
            for (PnlResult result : results) {
                actual = actual.add(value(result.getLines(), metric));
                budget = budget.add(value(result.getLines(), budgetKey(metric)));
            }
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("metricCode", metric);
        row.put("metricName", METRIC_NAMES.get(metric));
        row.put("actual", actual);
        row.put("budget", budget);
        row.put("gap", actual.subtract(budget));
        row.put("gapRate", gapRate(actual.subtract(budget), budget));
        return row;
    }

    private Map<String, Object> topImpacts(List<PnlResult> results, String metric, int limit) {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (PnlResult result : results) {
            BigDecimal actual = value(result.getLines(), metric);
            BigDecimal budget = value(result.getLines(), budgetKey(metric));
            BigDecimal gap = actual.subtract(budget);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("scopeCode", result.getScopeCode());
            item.put("metricCode", metric);
            item.put("actual", actual);
            item.put("budget", budget);
            item.put("gap", gap);
            item.put("gapRate", gapRate(gap, budget));
            items.add(item);
        }
        List<Map<String, Object>> positive = new ArrayList<Map<String, Object>>(items);
        List<Map<String, Object>> negative = new ArrayList<Map<String, Object>>(items);
        Comparator<Map<String, Object>> descending = byGap(true);
        Comparator<Map<String, Object>> ascending = byGap(false);
        Collections.sort(positive, descending);
        Collections.sort(negative, ascending);
        Map<String, Object> top = new LinkedHashMap<String, Object>();
        top.put("positive", new ArrayList<Map<String, Object>>(positive.subList(0, Math.min(limit, positive.size()))));
        top.put("negative", new ArrayList<Map<String, Object>>(negative.subList(0, Math.min(limit, negative.size()))));
        return top;
    }

    private Comparator<Map<String, Object>> byGap(final boolean descending) {
        return new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                BigDecimal l = (BigDecimal) left.get("gap");
                BigDecimal r = (BigDecimal) right.get("gap");
                int result = l.compareTo(r);
                return descending ? -result : result;
            }
        };
    }

    private String normalizeScenario(String scenario) {
        if (scenario == null || scenario.trim().isEmpty()) return "ACT_BGT";
        String normalized = scenario.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("ACT_BGT", "ACT_FC", "FC_BGT").contains(normalized)) {
            throw new IllegalArgumentException("不支持的分析口径: " + scenario);
        }
        return normalized;
    }

    private String normalizeMetric(String metric) {
        if (metric == null || metric.trim().isEmpty()) return "GROSS_PROFIT";
        String normalized = metric.trim().toUpperCase(Locale.ROOT);
        if (!METRICS.contains(normalized)) throw new IllegalArgumentException("不支持的指标: " + metric);
        return normalized;
    }

    private String budgetKey(String metric) {
        return "PROFIT_RATE".equals(metric) ? "BUDGET_PROFIT_RATE" : "BUDGET_" + metric;
    }

    private String gapKey(String metric) {
        return "PROFIT_RATE".equals(metric) ? "GAP_PROFIT_RATE" : "GAP_" + metric;
    }

    private BigDecimal value(Map<String, BigDecimal> values, String key) {
        BigDecimal value = values.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal gapRate(BigDecimal gap, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return gap.divide(base.abs(), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }
}
