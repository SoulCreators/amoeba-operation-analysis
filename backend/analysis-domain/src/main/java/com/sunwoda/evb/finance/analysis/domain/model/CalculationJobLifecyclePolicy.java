package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 计算作业状态生命周期策略。
 *
 * <p>状态转移必须显式声明在这里，未声明的转移一律拒绝。</p>
 */
public final class CalculationJobLifecyclePolicy {
    private static final Map<CalculationJobStatus, Set<CalculationJobStatus>> ALLOWED_TRANSITIONS =
            createTransitions();

    private CalculationJobLifecyclePolicy() {
    }

    public static void validate(CalculationJobStatus current, CalculationJobStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("计算作业目标状态不能为空");
        }
        if (current == null) {
            throw new IllegalStateException("计算作业当前状态不能为空，无法转移到: " + next);
        }
        Set<CalculationJobStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("计算作业状态不允许从 " + current + " 转移到 " + next);
        }
    }

    private static Map<CalculationJobStatus, Set<CalculationJobStatus>> createTransitions() {
        EnumMap<CalculationJobStatus, Set<CalculationJobStatus>> transitions =
                new EnumMap<CalculationJobStatus, Set<CalculationJobStatus>>(CalculationJobStatus.class);
        transitions.put(CalculationJobStatus.QUEUED, allowed(CalculationJobStatus.RUNNING));
        transitions.put(CalculationJobStatus.RUNNING,
                allowed(CalculationJobStatus.SUCCEEDED, CalculationJobStatus.FAILED));
        transitions.put(CalculationJobStatus.FAILED, allowed(CalculationJobStatus.QUEUED));
        transitions.put(CalculationJobStatus.SUCCEEDED, Collections.<CalculationJobStatus>emptySet());
        return Collections.unmodifiableMap(transitions);
    }

    private static Set<CalculationJobStatus> allowed(CalculationJobStatus... statuses) {
        EnumSet<CalculationJobStatus> allowed = EnumSet.noneOf(CalculationJobStatus.class);
        Collections.addAll(allowed, statuses);
        return Collections.unmodifiableSet(allowed);
    }
}
