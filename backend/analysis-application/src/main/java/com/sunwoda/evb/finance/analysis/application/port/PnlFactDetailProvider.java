package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;

import java.util.List;

/**
 * 查询已持久化的标准事实明细。实现必须在查询侧执行用户范围过滤，不能把全量事实交给上层再过滤。
 */
public interface PnlFactDetailProvider {
    List<PnlFact> findForUser(String batchNo, AnalysisPerspective perspective, String userId);
}
