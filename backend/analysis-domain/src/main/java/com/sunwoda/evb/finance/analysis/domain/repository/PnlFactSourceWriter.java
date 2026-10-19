package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;

import java.util.List;

/**
 * P&amp;L 事实来源追溯写入端口。
 *
 * <p>本阶段只定义替换写入和查询契约；具体实现可以是内存、数据库或其他存储。</p>
 */
public interface PnlFactSourceWriter {
    void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources);

    List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
            String batchNo, AnalysisPerspective perspective, String taskNo);
}
