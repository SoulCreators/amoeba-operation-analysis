package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;

import java.util.List;

public interface PnlFactProvider {
    List<PnlFact> load(AnalysisBatch batch);
}
