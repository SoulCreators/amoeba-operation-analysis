package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 失败处理状态机。
 *
 * <p>本次新增：人工重试和补偿只能从 MANUAL_REQUIRED 发起，任何未声明的状态转移都拒绝。</p>
 */
public final class CalculationRetryLifecyclePolicy {
    private static final Map<CalculationRetryStatus, Set<CalculationRetryStatus>> TRANSITIONS = create();

    private CalculationRetryLifecyclePolicy() {
    }

    public static void validate(CalculationRetryStatus current, CalculationRetryStatus next) {
        if (current == null || next == null) {
            throw new IllegalArgumentException("重试状态不能为空");
        }
        Set<CalculationRetryStatus> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("重试状态不允许从 " + current + " 转移到 " + next);
        }
    }

    private static Map<CalculationRetryStatus, Set<CalculationRetryStatus>> create() {
        EnumMap<CalculationRetryStatus, Set<CalculationRetryStatus>> result =
                new EnumMap<CalculationRetryStatus, Set<CalculationRetryStatus>>(CalculationRetryStatus.class);
        result.put(CalculationRetryStatus.ACTIVE, allowed(CalculationRetryStatus.PENDING_RETRY,
                CalculationRetryStatus.MANUAL_REQUIRED, CalculationRetryStatus.SUCCEEDED));
        result.put(CalculationRetryStatus.PENDING_RETRY, allowed(CalculationRetryStatus.PROCESSING));
        result.put(CalculationRetryStatus.PROCESSING, allowed(CalculationRetryStatus.PENDING_RETRY,
                CalculationRetryStatus.MANUAL_REQUIRED, CalculationRetryStatus.SUCCEEDED));
        result.put(CalculationRetryStatus.MANUAL_REQUIRED, allowed(CalculationRetryStatus.PROCESSING,
                CalculationRetryStatus.COMPENSATED));
        result.put(CalculationRetryStatus.SUCCEEDED, Collections.<CalculationRetryStatus>emptySet());
        result.put(CalculationRetryStatus.COMPENSATED, Collections.<CalculationRetryStatus>emptySet());
        return Collections.unmodifiableMap(result);
    }

    private static Set<CalculationRetryStatus> allowed(CalculationRetryStatus... statuses) {
        EnumSet<CalculationRetryStatus> result = EnumSet.noneOf(CalculationRetryStatus.class);
        Collections.addAll(result, statuses);
        return Collections.unmodifiableSet(result);
    }
}
