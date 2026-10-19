package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;

import java.util.List;

/** Reads row-level provenance without changing the aggregated P&amp;L fact flow. */
public interface PnlFactSourceReader {
    List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task);
}
