package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;

import java.util.List;

/**
 * User-scoped query port for row-level P&amp;L fact provenance.
 *
 * <p>Implementations must apply the supplied identity and detail scope in
 * the backing store. The application service remains the authorization gate
 * and also applies a defense-in-depth scope check to the returned rows.</p>
 */
public interface PnlFactTraceQuery {
    List<PnlFactSource> findForUser(String batchNo, AnalysisPerspective perspective,
                                    String taskNo, String userId);
}
