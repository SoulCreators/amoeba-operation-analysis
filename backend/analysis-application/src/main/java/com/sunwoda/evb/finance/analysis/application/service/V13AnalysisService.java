package com.sunwoda.evb.finance.analysis.application.service;

import java.util.Map;

/**
 * 经营分析平台 V1.3 后端能力。
 *
 * <p>这里不直接读取演示数据，所有可计算结果均来自已完成的批次计算结果。
 * 对尚未具备数据模型或业务口径的功能，返回明确的状态和阻塞项，供前端展示和后续补齐。</p>
 */
public interface V13AnalysisService {
    Map<String, Object> overview(String batchNo, String userId, String scenario);

    Map<String, Object> matrix(String batchNo, String userId, String metricCode);

    Map<String, Object> unitEfficiency(String batchNo, String userId);

    Map<String, Object> forecast(String batchNo, String userId, String scenario, String fcVersion);

    Map<String, Object> risk(String batchNo, String userId);

    Map<String, Object> waterfall(String batchNo, String userId);

    Map<String, Object> topic(String batchNo, String userId, String topicCode);
}
