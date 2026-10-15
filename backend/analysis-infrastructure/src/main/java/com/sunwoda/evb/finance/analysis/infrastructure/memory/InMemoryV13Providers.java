package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotProvider;
import com.sunwoda.evb.finance.analysis.application.port.FcVersionProvider;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactDetailProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Local-only fallbacks for V1.3 query ports. The calculation itself still reads
 * the validated Excel import through PoiPnlFactProvider; these adapters only
 * keep optional forecast/detail providers explicit when PostgreSQL is absent.
 */
@Repository
@Profile("local")
public class InMemoryV13Providers implements PnlFactDetailProvider, FcVersionProvider, FcSnapshotProvider {

    @Override
    public List<PnlFact> findForUser(String batchNo, AnalysisPerspective perspective, String userId) {
        return Collections.emptyList();
    }

    @Override
    public Optional<FcVersion> findForBatch(String batchNo, AnalysisPerspective perspective, String versionCode) {
        return Optional.empty();
    }

    @Override
    public List<FcSnapshot> findForBatch(String batchNo, AnalysisPerspective perspective,
                                         String versionCode, String userId) {
        return Collections.emptyList();
    }
}
