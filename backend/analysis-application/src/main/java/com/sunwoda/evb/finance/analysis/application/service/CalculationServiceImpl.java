package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CalculationServiceImpl implements CalculationService {
    private final AnalysisBatchRepository batchRepository;
    private final PnlFactProvider factProvider;
    private final Map<String, CalculationResult> results = new LinkedHashMap<String, CalculationResult>();

    public CalculationServiceImpl(AnalysisBatchRepository batchRepository, PnlFactProvider factProvider) {
        this.batchRepository = batchRepository;
        this.factProvider = factProvider;
    }

    @Override
    public synchronized CalculationResult calculate(String batchNo) {
        AnalysisBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
        if (batch.getStatus() == BatchStatus.PUBLISHED) {
            throw new IllegalStateException("已发布批次不可直接重算");
        }
        batch.moveTo(BatchStatus.CALCULATING);
        batchRepository.save(batch);
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
        results.put(batchNo, result);
        batch.moveTo(BatchStatus.CALCULATED);
        batchRepository.save(batch);
        return result;
    }

    @Override
    public synchronized CalculationResult get(String batchNo) {
        CalculationResult result = results.get(batchNo);
        if (result == null) throw new IllegalArgumentException("批次尚未完成计算: " + batchNo);
        return result;
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
        }

        Map<String, BigDecimal> toLines() {
            BigDecimal grossProfit = revenue.subtract(salesCost);
            BigDecimal otherLoss = assetImpairment.add(creditImpairment);
            BigDecimal netProfit = grossProfit.subtract(idleExpense).subtract(baseExpense)
                    .subtract(rdExpense).subtract(otherLoss).add(otherIncome);
            BigDecimal profitRate = revenue.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : netProfit.divide(revenue, 8, RoundingMode.HALF_UP);
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
            return lines;
        }
    }
}
