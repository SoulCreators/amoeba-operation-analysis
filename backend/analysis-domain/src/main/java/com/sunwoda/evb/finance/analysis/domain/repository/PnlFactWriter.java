package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;

import java.util.List;

/** 标准事实落库端口；数据库、数仓或测试替身均可实现。 */
public interface PnlFactWriter {
    void replace(AnalysisBatch batch, ImportTask task, List<PnlFact> facts);
}
