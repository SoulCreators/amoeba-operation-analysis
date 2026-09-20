package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryCalculationResultRepository implements CalculationResultRepository {
    private final ConcurrentMap<String, CalculationResult> store =
            new ConcurrentHashMap<String, CalculationResult>();

    @Override
    public CalculationResult save(CalculationResult result) {
        store.put(result.getBatchNo(), result);
        return result;
    }

    @Override
    public Optional<CalculationResult> findByBatchNo(String batchNo) {
        return Optional.ofNullable(store.get(batchNo));
    }
}
