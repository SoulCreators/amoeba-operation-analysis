package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 导入任务状态生命周期策略。
 *
 * <p>状态转移必须显式声明在这里，未声明的转移一律拒绝。</p>
 */
public final class ImportLifecyclePolicy {
    private static final Map<ImportStatus, Set<ImportStatus>> ALLOWED_TRANSITIONS = createTransitions();

    private ImportLifecyclePolicy() {
    }

    public static void validate(ImportStatus current, ImportStatus next) {
        if (next == null) {
            throw new IllegalArgumentException("导入状态不能为空");
        }
        if (current == null) {
            throw new IllegalStateException("当前导入状态不能为空，无法转移到: " + next);
        }
        Set<ImportStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("导入状态不允许从 " + current + " 转移到 " + next);
        }
    }

    private static Map<ImportStatus, Set<ImportStatus>> createTransitions() {
        EnumMap<ImportStatus, Set<ImportStatus>> transitions =
                new EnumMap<ImportStatus, Set<ImportStatus>>(ImportStatus.class);
        transitions.put(ImportStatus.CREATED, allowed(ImportStatus.VALIDATING, ImportStatus.CANCELLED));
        transitions.put(ImportStatus.VALIDATING, allowed(ImportStatus.VALID, ImportStatus.INVALID));
        transitions.put(ImportStatus.INVALID, allowed(ImportStatus.VALIDATING));
        transitions.put(ImportStatus.VALID, allowed(ImportStatus.VALID, ImportStatus.VALIDATING));
        transitions.put(ImportStatus.CANCELLED, allowed(ImportStatus.CANCELLED));
        return Collections.unmodifiableMap(transitions);
    }

    private static Set<ImportStatus> allowed(ImportStatus... statuses) {
        EnumSet<ImportStatus> allowed = EnumSet.noneOf(ImportStatus.class);
        Collections.addAll(allowed, statuses);
        return Collections.unmodifiableSet(allowed);
    }
}
