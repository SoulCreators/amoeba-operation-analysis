package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 批次状态生命周期策略。
 *
 * <p>状态转移必须显式声明在这里，未声明的转移一律拒绝。</p>
 */
public final class BatchLifecyclePolicy {
    private static final Map<BatchStatus, Set<BatchStatus>> ALLOWED_TRANSITIONS = createTransitions();

    private BatchLifecyclePolicy() {
    }

    public static void validate(BatchStatus current, BatchStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("批次状态不能为空");
        }
        if (current == null) {
            throw new IllegalStateException("当前批次状态不能为空，无法转移到: " + next);
        }
        Set<BatchStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("批次状态不允许从 " + current + " 转移到 " + next);
        }
    }

    private static Map<BatchStatus, Set<BatchStatus>> createTransitions() {
        EnumMap<BatchStatus, Set<BatchStatus>> transitions =
                new EnumMap<BatchStatus, Set<BatchStatus>>(BatchStatus.class);
        transitions.put(BatchStatus.DRAFT, allowed(BatchStatus.CHECKING));
        transitions.put(BatchStatus.CHECKING, allowed(BatchStatus.READY_FOR_CALCULATION, BatchStatus.FAILED));
        transitions.put(BatchStatus.READY_FOR_CALCULATION, allowed(BatchStatus.CALCULATING));
        transitions.put(BatchStatus.CALCULATING, allowed(BatchStatus.CALCULATED, BatchStatus.FAILED));
        transitions.put(BatchStatus.CALCULATED, allowed(BatchStatus.CALCULATING, BatchStatus.CONFIRMED));
        transitions.put(BatchStatus.CONFIRMED, allowed(BatchStatus.PUBLISHED));
        transitions.put(BatchStatus.FAILED, allowed(BatchStatus.CHECKING));
        transitions.put(BatchStatus.PUBLISHED, allowed(BatchStatus.PUBLISHED));
        return Collections.unmodifiableMap(transitions);
    }

    private static Set<BatchStatus> allowed(BatchStatus... statuses) {
        EnumSet<BatchStatus> allowed = EnumSet.noneOf(BatchStatus.class);
        Collections.addAll(allowed, statuses);
        return Collections.unmodifiableSet(allowed);
    }
}
