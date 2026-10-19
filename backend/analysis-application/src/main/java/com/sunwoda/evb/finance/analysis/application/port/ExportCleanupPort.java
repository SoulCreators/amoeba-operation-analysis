package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;

import java.time.LocalDateTime;
import java.util.List;

/** Internal worker port for expiring tasks and claiming file cleanup work. */
public interface ExportCleanupPort {
    void expireDueTasks(LocalDateTime now);

    List<ExportCleanupTask> claimExpired(String workerId, int limit,
                                         LocalDateTime now, LocalDateTime leaseUntil);

    void markCleanupSucceeded(String jobId, String claimToken, LocalDateTime now);

    void markCleanupRetry(String jobId, String claimToken, String errorMessage,
                          LocalDateTime nextAttemptAt, LocalDateTime now,
                          boolean terminal);
}
