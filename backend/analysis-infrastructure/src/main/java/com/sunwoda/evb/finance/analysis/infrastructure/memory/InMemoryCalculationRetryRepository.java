package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaim;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryClaimLostException;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryState;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 失败处理内存仓储。
 *
 * <p>本次新增：为独立测试和 memory profile 提供 request_key 唯一、到期查询的本地实现。</p>
 * <p>本次补充：与既有 CalculationJob 内存仓储保持一致，不限制 profile，供 API 默认内存上下文使用。</p>
 */
@Repository
public class InMemoryCalculationRetryRepository implements CalculationRetryRepository {
    private final ConcurrentMap<String, CalculationRetryState> byJobId =
            new ConcurrentHashMap<String, CalculationRetryState>();
    private final ConcurrentMap<String, CalculationRetryClaim> claims =
            new ConcurrentHashMap<String, CalculationRetryClaim>();

    @Override
    public synchronized CalculationRetryState save(CalculationRetryState state) {
        if (state == null) throw new IllegalArgumentException("state 不能为空");
        for (CalculationRetryState existing : byJobId.values()) {
            if (!existing.getJobId().equals(state.getJobId())
                    && existing.getRequestKey().equals(state.getRequestKey())) {
                throw new IllegalStateException("requestKey 已存在: " + state.getRequestKey());
            }
        }
        byJobId.put(state.getJobId(), state);
        return state;
    }

    @Override
    public synchronized Optional<CalculationRetryState> findByJobId(String jobId) {
        return Optional.ofNullable(byJobId.get(required(jobId, "jobId")));
    }

    @Override
    public synchronized Optional<CalculationRetryState> findByRequestKey(String requestKey) {
        String required = required(requestKey, "requestKey");
        for (CalculationRetryState state : byJobId.values()) {
            if (required.equals(state.getRequestKey())) return Optional.of(state);
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<CalculationRetryState> findDue(LocalDateTime now, int limit) {
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        List<CalculationRetryState> result = new ArrayList<CalculationRetryState>();
        for (CalculationRetryState state : byJobId.values()) {
            if (state.getStatus() == CalculationRetryStatus.PENDING_RETRY
                    && state.getNextRetryAt() != null && !state.getNextRetryAt().isAfter(now)) {
                result.add(state);
            }
        }
        Collections.sort(result, new Comparator<CalculationRetryState>() {
            @Override
            public int compare(CalculationRetryState left, CalculationRetryState right) {
                return left.getNextRetryAt().compareTo(right.getNextRetryAt());
            }
        });
        return result.size() <= limit ? result : new ArrayList<CalculationRetryState>(result.subList(0, limit));
    }

    @Override
    public synchronized List<CalculationRetryClaim> findDueClaims(LocalDateTime now, int limit) {
        if (now == null) throw new IllegalArgumentException("now 不能为空");
        if (limit <= 0) throw new IllegalArgumentException("limit 必须大于 0");
        List<CalculationRetryClaim> result = new ArrayList<CalculationRetryClaim>();
        List<CalculationRetryState> due = findDue(now, limit);
        for (CalculationRetryState state : due) {
            CalculationRetryClaim existing = claims.get(state.getJobId());
            if (existing != null && existing.getClaimedUntil().isAfter(now)) continue;
            CalculationRetryClaim claim = new CalculationRetryClaim(state,
                    "memory-" + java.util.UUID.randomUUID().toString(), now.plusMinutes(5L));
            claims.put(state.getJobId(), claim);
            result.add(claim);
        }
        return result;
    }

    @Override
    public synchronized void lockClaim(String jobId, String claimToken, LocalDateTime now) {
        CalculationRetryClaim claim = claims.get(required(jobId, "jobId"));
        if (claim == null || !claim.getClaimToken().equals(claimToken)
                || !claim.getClaimedUntil().isAfter(now)) {
            throw new CalculationRetryClaimLostException("计算重试租约已失效: jobId=" + jobId);
        }
    }

    @Override
    public synchronized void releaseClaim(String jobId, String claimToken) {
        CalculationRetryClaim claim = claims.get(required(jobId, "jobId"));
        if (claim != null && claim.getClaimToken().equals(claimToken)) {
            // 本次新增：内存实现也按 token 释放，保持与 JDBC 所有权语义一致。
            claims.remove(jobId);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }
}
