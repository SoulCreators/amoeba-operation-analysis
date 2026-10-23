package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;

import java.math.BigDecimal;
import java.util.List;

/** 简易调整单应用服务：录入、查询、作废，以及供普通计算叠加层消费的生效调整。 */
public interface AdjustmentService {
    Adjustment create(String period, AnalysisPerspective perspective, String scopeCode,
                      AdjustmentScenario scenario, String metricCode, BigDecimal amount,
                      AdjustmentDirection direction, String reason, String evidenceFileId,
                      String expectedVersion, String operator);

    List<Adjustment> list(String period, AnalysisPerspective perspective, String userId);

    Adjustment voidAdjustment(String adjustmentId, String reason, String operator);

    /** 贯通普通计算：返回期间 + 视角下全部生效调整，供计算叠加层叠加。 */
    List<Adjustment> listActive(String period, AnalysisPerspective perspective);
}
