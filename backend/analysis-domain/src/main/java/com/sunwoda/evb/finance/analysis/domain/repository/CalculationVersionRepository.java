package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

import java.util.List;
import java.util.Optional;

/**
 * 计算结果版本头仓储契约。
 *
 * <p>本次修改：提供版本追加、当前版本和历史版本读取的纯端口，
 * 不规定 JDBC、事务或权限实现。</p>
 */
public interface CalculationVersionRepository {
    CalculationVersion save(CalculationVersion version);

    Optional<CalculationVersion> findById(String calculationVersionId);

    Optional<CalculationVersion> findCurrentByBatchNo(String batchNo, AnalysisPerspective perspective);

    List<CalculationVersion> findByBatchNo(String batchNo, AnalysisPerspective perspective);

    /**
     * 按追踪号定位计算版本，供审计/历史配置重放只读链路使用。
     *
     * <p>本次新增：追踪号是计算血缘的稳定查询入口；同一追踪号在正常流程下至多
     * 一条版本。默认抛出不支持异常，保证旧的内存测试替身不因接口扩展而失效。</p>
     */
    default Optional<CalculationVersion> findByTraceId(String traceId) {
        throw new UnsupportedOperationException("按追踪号查询版本尚未由实现提供");
    }
}
