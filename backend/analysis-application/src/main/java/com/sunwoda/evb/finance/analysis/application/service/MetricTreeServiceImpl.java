package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.MetricResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class MetricTreeServiceImpl implements MetricTreeService {
    private final CalculationService calculationService;
    private final MetricDefinitionRepository metricRepository;

    public MetricTreeServiceImpl(CalculationService calculationService,
                                 MetricDefinitionRepository metricRepository) {
        this.calculationService = calculationService;
        this.metricRepository = metricRepository;
    }

    @Override
    public List<MetricResult> calculate(String batchNo, String userId) {
        CalculationResult calculation = calculationService.getForUser(batchNo, userId);
        List<MetricDefinition> definitions = metricRepository.list(
                calculation.getPerspective().getCode(), false);
        List<MetricResult> result = new ArrayList<MetricResult>();
        for (PnlResult pnl : calculation.getResults()) {
            for (MetricDefinition definition : definitions) {
                BigDecimal actual = evaluate(definition.getFormulaExpression(), pnl.getLines(), false);
                BigDecimal budget = evaluate(definition.getFormulaExpression(), pnl.getLines(), true);
                result.add(new MetricResult(pnl.getScopeCode(), definition.getMetricCode(),
                        definition.getMetricName(), definition.getUnit(), actual, budget,
                        actual.subtract(budget), definition.getAiSummaryTemplate()));
            }
        }
        return result;
    }

    private BigDecimal evaluate(String formula, java.util.Map<String, BigDecimal> lines, boolean budget) {
        String expression = formula == null ? "0" : formula.toUpperCase(Locale.ROOT)
                .replace("SUM(", "").replace(")", "").replace(" ", "");
        String[] plusParts = expression.split("\\+");
        BigDecimal total = BigDecimal.ZERO;
        for (String plusPart : plusParts) total = total.add(evaluateProduct(plusPart, lines, budget));
        return total;
    }

    private BigDecimal evaluateProduct(String expression, java.util.Map<String, BigDecimal> lines,
                                       boolean budget) {
        String[] minusParts = expression.split("-");
        BigDecimal value = evaluateDivision(minusParts[0], lines, budget);
        for (int i = 1; i < minusParts.length; i++) {
            value = value.subtract(evaluateDivision(minusParts[i], lines, budget));
        }
        return value;
    }

    private BigDecimal evaluateDivision(String expression, java.util.Map<String, BigDecimal> lines,
                                         boolean budget) {
        String[] parts = expression.split("/");
        BigDecimal value = token(parts[0], lines, budget);
        for (int i = 1; i < parts.length; i++) {
            BigDecimal divisor = token(parts[i], lines, budget);
            value = divisor.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : value.divide(divisor, 8, RoundingMode.HALF_UP);
        }
        return value;
    }

    private BigDecimal token(String token, java.util.Map<String, BigDecimal> lines, boolean budget) {
        String key = token.trim();
        if (key.isEmpty()) return BigDecimal.ZERO;
        if (key.matches("-?\\d+(\\.\\d+)?")) return new BigDecimal(key);
        if (budget && !key.startsWith("BUDGET_")) key = "BUDGET_" + key;
        BigDecimal value = lines.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }
}
