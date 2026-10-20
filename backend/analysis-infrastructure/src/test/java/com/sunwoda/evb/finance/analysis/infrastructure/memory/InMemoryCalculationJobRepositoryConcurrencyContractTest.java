package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存计算作业仓储的并发契约测试。
 *
 * <p>测试范围仅限一个内存仓储实例内的当前实现，不推导跨线程进程之外的幂等语义。</p>
 */
class InMemoryCalculationJobRepositoryConcurrencyContractTest {

    @Test
    void concurrentDifferentJobsWithSameActiveRequestKeyAllowAtMostOneSave() throws Exception {
        InMemoryCalculationJobRepository repository = new InMemoryCalculationJobRepository();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = submitSave(repository, job("job-1", "shared-request"), ready, start, executor);
            Future<Boolean> second = submitSave(repository, job("job-2", "shared-request"), ready, start, executor);

            // 先确保两个调用方都已就绪，再同时放行，覆盖检查与保存的并发入口。
            assertTrue(ready.await(5, TimeUnit.SECONDS), "两个保存线程未能在限定时间内就绪");
            start.countDown();

            int successfulSaves = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successfulSaves, "同一活动 requestKey 至多只能保存一个作业");
            assertTrue(repository.findActiveByRequestKey("shared-request").isPresent());
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failedAndSucceededJobsAreNotReturnedByActiveRequestKeyLookup() {
        InMemoryCalculationJobRepository repository = new InMemoryCalculationJobRepository();

        CalculationJob failed = job("failed-job", "failed-request");
        repository.save(failed);
        failed.moveTo(CalculationJobStatus.RUNNING);
        failed.fail("计算失败");
        repository.save(failed);

        CalculationJob succeeded = job("succeeded-job", "succeeded-request");
        repository.save(succeeded);
        succeeded.moveTo(CalculationJobStatus.RUNNING);
        succeeded.moveTo(CalculationJobStatus.SUCCEEDED);
        repository.save(succeeded);

        // 终态作业仍保留在按 jobId 的存储中，但不再占用活动 requestKey。
        assertEquals(CalculationJobStatus.FAILED, failed.getStatus());
        assertTrue(!repository.findActiveByRequestKey("failed-request").isPresent());
        assertEquals(CalculationJobStatus.SUCCEEDED, succeeded.getStatus());
        assertTrue(!repository.findActiveByRequestKey("succeeded-request").isPresent());
    }

    @Test
    void sameJobIdRejectsReplacementWithoutOverwritingExistingJob() {
        InMemoryCalculationJobRepository repository = new InMemoryCalculationJobRepository();
        CalculationJob original = job("same-job", "original-request");

        assertEquals("same-job", repository.save(original).getJobId());
        original.moveTo(CalculationJobStatus.RUNNING);
        assertEquals(CalculationJobStatus.RUNNING, repository.save(original).getStatus());
        assertEquals("original-request", repository.findByJobId("same-job").get().getRequestKey());

        // 本次修改锁定当前实现对重复 jobId 的拒绝，避免不同对象覆盖既有作业。
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.save(job("same-job", "replacement-request")));
        assertTrue(error.getMessage().contains("jobId"));
        assertEquals(CalculationJobStatus.RUNNING,
                repository.findByJobId("same-job").get().getStatus());
    }

    private static Future<Boolean> submitSave(InMemoryCalculationJobRepository repository,
                                              CalculationJob job,
                                              CountDownLatch ready,
                                              CountDownLatch start,
                                              ExecutorService executor) {
        return executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("保存线程未在限定时间内开始");
            }
            try {
                repository.save(job);
                return true;
            } catch (IllegalStateException expected) {
                // 另一个线程已占用活动 requestKey 时，当前实现以异常拒绝保存。
                return false;
            }
        });
    }

    private static CalculationJob job(String jobId, String requestKey) {
        return new CalculationJob(jobId, requestKey, "batch-1",
                LocalDateTime.of(2026, 10, 19, 10, 0));
    }
}
