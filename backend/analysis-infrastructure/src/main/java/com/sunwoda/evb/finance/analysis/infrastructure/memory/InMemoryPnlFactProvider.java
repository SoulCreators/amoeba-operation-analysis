package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Repository
public class InMemoryPnlFactProvider implements PnlFactProvider {
    @Override
    public List<PnlFact> load(AnalysisBatch batch) {
        if (batch.getPerspective() == AnalysisPerspective.BASE) {
            return Arrays.asList(
                    fact("NANCHANG", 1000, 1200, 900, 15, 60, 0, 5, 0, 3),
                    fact("YICHANG", 800, 900, 680, 8, 45, 0, 0, 0, 2),
                    fact("THAILAND", 600, 700, 560, 6, 30, 0, 0, 0, 0));
        }
        if (batch.getPerspective() == AnalysisPerspective.PRODUCT_LINE) {
            return Arrays.asList(
                    fact("POWER", 1400, 1750, 1320, 12, 35, 70, 8, 0, 5),
                    fact("ENERGY", 500, 650, 480, 5, 20, 20, 0, 0, 1));
        }
        return Arrays.asList(
                fact("AUTOMOTIVE", 1500, 1900, 1430, 10, 50, 40, 6, 0, 4),
                fact("STORAGE", 400, 500, 370, 7, 15, 10, 2, 0, 1));
    }

    private PnlFact fact(String scope, double volume, double revenue, double salesCost,
                         double idle, double base, double rd, double asset, double credit,
                         double otherIncome) {
        return new PnlFact(scope, decimal(volume), decimal(revenue), decimal(salesCost),
                decimal(idle), decimal(base), decimal(rd), decimal(asset), decimal(credit),
                decimal(otherIncome));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
