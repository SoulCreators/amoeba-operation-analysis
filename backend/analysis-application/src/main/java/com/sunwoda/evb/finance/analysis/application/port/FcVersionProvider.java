package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;

import java.util.Optional;

/** 查询 FC 版本元数据；实现不得用预算或历史 FC 自动替代缺失版本。 */
public interface FcVersionProvider {
    Optional<FcVersion> findForBatch(String batchNo, AnalysisPerspective perspective, String versionCode);
}
