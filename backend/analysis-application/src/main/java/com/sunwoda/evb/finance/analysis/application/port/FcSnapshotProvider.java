package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;

import java.util.List;

/** 查询指定 FC 版本的预测快照；实现必须在查询侧执行用户范围过滤。 */
public interface FcSnapshotProvider {
    List<FcSnapshot> findForBatch(String batchNo, AnalysisPerspective perspective,
                                  String versionCode, String userId);
}
