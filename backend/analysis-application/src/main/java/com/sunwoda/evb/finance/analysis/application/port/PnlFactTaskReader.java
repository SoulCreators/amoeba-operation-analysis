package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;

import java.util.List;

/** 将单个已校验导入任务标准化为事实行，供文件链路和数据库落库链路共用。 */
public interface PnlFactTaskReader {
    List<PnlFact> read(AnalysisBatch batch, ImportTask task);
}
