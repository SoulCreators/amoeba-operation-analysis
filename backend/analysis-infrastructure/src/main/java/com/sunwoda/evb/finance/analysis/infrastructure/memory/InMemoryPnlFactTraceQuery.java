package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactTraceQuery;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Local-profile trace query adapter. The application service applies the
 * user's detail scopes as a defense-in-depth filter; production DB queries
 * must use {@code JdbcPnlFactSourceProvider} for database-side filtering.
 */
@Repository
@Profile("!db")
public class InMemoryPnlFactTraceQuery implements PnlFactTraceQuery {
    private final PnlFactSourceWriter sourceWriter;

    public InMemoryPnlFactTraceQuery(PnlFactSourceWriter sourceWriter) {
        if (sourceWriter == null) {
            throw new IllegalArgumentException("sourceWriter must not be null");
        }
        this.sourceWriter = sourceWriter;
    }

    @Override
    public List<PnlFactSource> findForUser(String batchNo, AnalysisPerspective perspective,
                                           String taskNo, String userId) {
        return sourceWriter.findByBatchNoAndPerspectiveAndTaskNo(batchNo, perspective, taskNo);
    }
}
