package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 计算作业执行阶段。
 *
 * <p>阶段顺序固定，对应财经计算的标准化、映射、分摊、损益、PVM/专题、零容差勾稽与结果物化；
 * 每个阶段按序执行一次，进度与失败原因以独立阶段日志记录，不把部分计算结果暴露为正式金额。</p>
 */
public enum CalculationStage {
    NORMALIZE,
    MAP,
    ALLOCATE,
    PNL,
    PVM,
    RECONCILE,
    MATERIALIZE
}
