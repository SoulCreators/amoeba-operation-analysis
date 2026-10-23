package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;

import java.util.Collections;
import java.util.List;

/**
 * 非财经指标计算摘要：配对结果、未匹配型号数与多巴长冲突数。
 *
 * <p>多巴长冲突是发布门禁的阻断输入：冲突数大于 0 时不得发布。</p>
 */
public class NonfinancialCalculationSummary {
    private final int total;
    private final int unmatchedCount;
    private final int conflictCount;
    private final List<NonfinancialResult> results;

    public NonfinancialCalculationSummary(int total, int unmatchedCount, int conflictCount,
                                          List<NonfinancialResult> results) {
        this.total = total;
        this.unmatchedCount = unmatchedCount;
        this.conflictCount = conflictCount;
        this.results = Collections.unmodifiableList(results);
    }

    public int getTotal() { return total; }
    public int getUnmatchedCount() { return unmatchedCount; }
    public int getConflictCount() { return conflictCount; }
    public List<NonfinancialResult> getResults() { return results; }

    /** 发布门禁：存在多巴长冲突即阻断发布。 */
    public boolean hasPublishBlockingConflict() { return conflictCount > 0; }
}
