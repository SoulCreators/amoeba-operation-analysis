package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.NonfinancialCalculationSummary;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;

import java.util.List;

/**
 * 非财经指标（电芯优率）导入、计算与巴长授权查询服务。
 */
public interface NonfinancialMetricService {
    /** 长表导入：重复阻断，同期整期替换。返回导入事实数。 */
    int importFacts(String batchNo, List<NonfinancialFact> facts, String uploadedBy);

    /** 目标/实际配对计算，解析巴长并标记多巴长冲突（冲突阻断发布）。 */
    NonfinancialCalculationSummary calculate(String batchNo);

    /** 服务端授权查询：总部财经全域、基地财经按基地、巴长按本人工号映射型号过滤。 */
    List<NonfinancialResult> listVisible(String period, String userId);

    /** 发布门禁：同期无多巴长冲突方可发布，返回发布记录。 */
    NonfinancialPublication publish(String period, String operator);

    /** 查询某期间发布状态（无记录返回草稿态）。 */
    NonfinancialPublication getPublication(String period);
}
