package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;

import java.util.Optional;

public interface CalculationResultRepository {
    CalculationResult save(CalculationResult result);
    Optional<CalculationResult> findByBatchNo(String batchNo);

    default Optional<CalculationResult> findVersionForUser(String batchNo, String versionNo, String userId) {
        return Optional.empty();
    }

    /**
     * Reads the result visible to one user. Implementations backed by a
     * database may apply the user's VIEW_SCOPE predicate before materializing
     * result rows; lightweight repositories retain the historical behavior.
     */
    default Optional<CalculationResult> findByBatchNoForUser(String batchNo, String userId) {
        return findByBatchNo(batchNo);
    }

    /**
     * Reads only result metadata when a user-scoped read has no visible rows.
     * Database implementations can avoid materializing any result scope or
     * result-line data while callers retain the historical status errors.
     */
    default Optional<CalculationResult> findMetadataByBatchNo(String batchNo) {
        return findByBatchNo(batchNo);
    }
}
