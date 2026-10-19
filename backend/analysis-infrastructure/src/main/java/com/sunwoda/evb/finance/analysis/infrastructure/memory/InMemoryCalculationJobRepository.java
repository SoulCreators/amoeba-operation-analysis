package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 计算作业内存仓储。
 *
 * <p>保存操作采用实例级互斥，确保活动请求键检查与写入是一个原子操作。</p>
 * <p>该实现只用于当前隔离切片和测试，不代表跨进程幂等控制。</p>
 */
@Repository
public class InMemoryCalculationJobRepository implements CalculationJobRepository {
    private final ConcurrentMap<String, CalculationJob> store =
            new ConcurrentHashMap<String, CalculationJob>();

    @Override
    public synchronized CalculationJob save(CalculationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job 不能为空");
        }

        CalculationJob existing = store.get(job.getJobId());
        if (existing != null && existing != job) {
            throw new IllegalStateException("jobId 已存在: " + job.getJobId());
        }

        CalculationJob active = findActiveByRequestKeyInternal(job.getRequestKey());
        if (active != null && active != job) {
            throw new IllegalStateException("requestKey 已有活动作业: " + job.getRequestKey());
        }

        store.put(job.getJobId(), job);
        return job;
    }

    @Override
    public synchronized Optional<CalculationJob> findByJobId(String jobId) {
        return Optional.ofNullable(store.get(requiredText(jobId, "jobId")));
    }

    @Override
    public synchronized Optional<CalculationJob> findActiveByRequestKey(String requestKey) {
        return Optional.ofNullable(findActiveByRequestKeyInternal(requiredText(requestKey, "requestKey")));
    }

    private CalculationJob findActiveByRequestKeyInternal(String requestKey) {
        for (CalculationJob job : store.values()) {
            if (requestKey.equals(job.getRequestKey()) && isActive(job)) {
                return job;
            }
        }
        return null;
    }

    private static boolean isActive(CalculationJob job) {
        return job.getStatus() == CalculationJobStatus.QUEUED
                || job.getStatus() == CalculationJobStatus.RUNNING;
    }

    private static String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
