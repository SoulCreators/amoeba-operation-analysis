package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupAlertPort;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupPort;
import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Deletes the bytes belonging to logically expired export tasks.
 *
 * <p>This worker deliberately has no download or authorization dependency. The
 * application port owns task lifecycle and the storage port owns the scoped,
 * idempotent delete operation.</p>
 */
@Component
@Profile("db & filesystem")
public class ExportCleanupWorker {
    static final int DEFAULT_BATCH_SIZE = 100;
    static final long CLEANUP_DELAY_MILLIS = 10 * 60 * 1000L;
    static final int CLAIM_LEASE_MINUTES = 5;
    static final int FIRST_RETRY_DELAY_MINUTES = 5;
    static final int SECOND_RETRY_DELAY_MINUTES = 10;

    private final ExportCleanupPort cleanupPort;
    private final ExportFileStoragePort storage;
    private final ExportCleanupAlertPort alert;
    private final String workerId;
    private final int batchSize;

    @Autowired
    public ExportCleanupWorker(
            ExportCleanupPort cleanupPort,
            ExportFileStoragePort storage,
            ExportCleanupAlertPort alert,
            @Value("${analysis.worker.cleanup.worker-id:analysis-worker}") String workerId,
            @Value("${analysis.worker.cleanup.batch-size:100}") int batchSize) {
        if (cleanupPort == null) throw new IllegalArgumentException("cleanupPort must not be null");
        if (storage == null) throw new IllegalArgumentException("storage must not be null");
        if (alert == null) throw new IllegalArgumentException("alert must not be null");
        if (workerId == null || workerId.trim().isEmpty()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");
        this.cleanupPort = cleanupPort;
        this.storage = storage;
        this.alert = alert;
        this.workerId = workerId.trim();
        this.batchSize = batchSize;
    }

    ExportCleanupWorker(ExportCleanupPort cleanupPort,
                        ExportFileStoragePort storage,
                        ExportCleanupAlertPort alert) {
        this(cleanupPort, storage, alert, "test-worker", DEFAULT_BATCH_SIZE);
    }

    /** Runs the cleanup loop every ten minutes when both production profiles are active. */
    @Scheduled(fixedDelay = CLEANUP_DELAY_MILLIS)
    public void scheduledCleanup() {
        runOnce(LocalDateTime.now());
    }

    /**
     * Executes one bounded cleanup pass at the supplied time.
     *
     * <p>Expiration is advanced before claiming. Each claimed task is handled
     * independently so one storage failure does not prevent other claims from
     * being attempted.</p>
     */
    public void runOnce(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("now must not be null");

        cleanupPort.expireDueTasks(now);
        List<ExportCleanupTask> tasks = cleanupPort.claimExpired(
                workerId, batchSize, now, now.plusMinutes(CLAIM_LEASE_MINUTES));
        for (ExportCleanupTask task : tasks == null
                ? Collections.<ExportCleanupTask>emptyList() : tasks) {
            cleanupOne(task, now);
        }
    }

    private void cleanupOne(ExportCleanupTask task, LocalDateTime now) {
        if (task == null) return;
        try {
            storage.delete(task.getFileKey(), task.getUserId());
            cleanupPort.markCleanupSucceeded(task.getJobId(), task.getClaimToken(), now);
        } catch (Exception failure) {
            handleFailure(task, now, failure);
        }
    }

    private void handleFailure(ExportCleanupTask task, LocalDateTime now, Exception failure) {
        int attempts = task.getAttempts();
        boolean terminal = attempts >= 3;
        if (!terminal) {
            long delay = attempts <= 1 ? FIRST_RETRY_DELAY_MINUTES : SECOND_RETRY_DELAY_MINUTES;
            cleanupPort.markCleanupRetry(task.getJobId(), task.getClaimToken(),
                    message(failure), now.plusMinutes(delay), now, false);
        } else {
            cleanupPort.markCleanupRetry(task.getJobId(), task.getClaimToken(),
                    message(failure), now, now, true);
            alert.alert(task, message(failure));
        }
    }

    private String message(Exception failure) {
        String detail = failure.getMessage();
        return detail == null || detail.trim().isEmpty()
                ? failure.getClass().getName() : detail;
    }
}
