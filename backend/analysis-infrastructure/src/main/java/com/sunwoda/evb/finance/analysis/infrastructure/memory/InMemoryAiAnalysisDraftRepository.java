package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryAiAnalysisDraftRepository implements AiAnalysisDraftRepository {
    private final ConcurrentMap<String, AiAnalysisDraft> store =
            new ConcurrentHashMap<String, AiAnalysisDraft>();

    @Override
    public AiAnalysisDraft save(AiAnalysisDraft draft) {
        store.put(draft.getBatchNo(), draft);
        return draft;
    }

    @Override
    public Optional<AiAnalysisDraft> findByBatchNo(String batchNo) {
        return Optional.ofNullable(store.get(batchNo));
    }
}
