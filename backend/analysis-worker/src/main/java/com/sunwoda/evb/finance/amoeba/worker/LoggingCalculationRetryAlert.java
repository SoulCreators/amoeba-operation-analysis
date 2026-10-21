package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryAlertPort;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 计算重试人工接管的默认日志告警适配器。
 *
 * <p>本次新增：沿用 LoggingExportCleanupAlert 的 profile 与结构，以结构化日志交给生产监控系统接管。</p>
 */
@Component
@Profile("db")
public class LoggingCalculationRetryAlert implements CalculationRetryAlertPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingCalculationRetryAlert.class);

    @Override
    public void alert(CalculationJob job, CalculationRetryDecision decision) {
        // 本次新增：仅输出已由应用服务确认的 MANUAL_REQUIRED 决策，不在适配器中变更业务状态。
        LOGGER.error("计算重试达到人工接管条件: jobId={}, batchNo={}, attemptNo={}, error={}",
                job.getJobId(), job.getBatchNo(), decision.getOriginalAttemptNo(), decision.getError());
    }
}
