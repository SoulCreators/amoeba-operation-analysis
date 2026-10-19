package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCalculationJobRepositoryTest {
    private final InMemoryCalculationJobRepository repository =
            new InMemoryCalculationJobRepository();

    @Test
    void savesAndFindsJobById() {
        CalculationJob job = job("job-1", "request-1");

        assertSame(job, repository.save(job));
        assertSame(job, repository.findByJobId("job-1").get());
        assertTrue(repository.findActiveByRequestKey("request-1").isPresent());
    }

    @Test
    void rejectsAnotherActiveJobWithSameRequestKey() {
        repository.save(job("job-1", "request-1"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.save(job("job-2", "request-1")));

        assertTrue(error.getMessage().contains("requestKey"));
    }

    @Test
    void allowsNewJobAfterPreviousJobReachesTerminalState() {
        CalculationJob first = job("job-1", "request-1");
        repository.save(first);
        first.moveTo(CalculationJobStatus.RUNNING);
        first.moveTo(CalculationJobStatus.SUCCEEDED);
        repository.save(first);

        CalculationJob second = job("job-2", "request-1");
        repository.save(second);

        assertSame(second, repository.findActiveByRequestKey("request-1").get());
        assertEquals(CalculationJobStatus.SUCCEEDED, repository.findByJobId("job-1").get().getStatus());
    }

    @Test
    void allowsUpdatingTheSameObjectAndRejectsAnotherObjectWithSameJobId() {
        CalculationJob original = job("job-1", "request-1");
        assertSame(original, repository.save(original));
        original.moveTo(CalculationJobStatus.RUNNING);
        assertSame(original, repository.save(original));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.save(job("job-1", "request-2")));

        assertTrue(error.getMessage().contains("jobId"));
    }

    @Test
    void rejectsNullAndBlankArguments() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByJobId(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByJobId(" "));
        assertThrows(IllegalArgumentException.class, () -> repository.findActiveByRequestKey(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findActiveByRequestKey(" "));
    }

    private static CalculationJob job(String jobId, String requestKey) {
        return new CalculationJob(jobId, requestKey, "batch-1", LocalDateTime.of(2026, 10, 19, 10, 0));
    }
}
