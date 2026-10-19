package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.OverviewResultProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * Local profile deliberately starts without business facts.
 *
 * <p>The platform must never manufacture KPI values when no real batch result
 * has been imported and calculated. Production deployments provide a database
 * backed implementation; local verification gets real values only after the
 * import/calculation workflow has supplied them.</p>
 */
@Repository
public class InMemoryOverviewResultProvider implements OverviewResultProvider {
    @Override
    public List<OverviewMetricResult> query(String period, AnalysisPerspective perspective, String scope) {
        return Collections.emptyList();
    }
}
