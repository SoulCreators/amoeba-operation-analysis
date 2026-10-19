package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupAlertPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Default operational alert sink; production monitoring can bridge this log. */
@Component
@Profile("db & filesystem")
public class LoggingExportCleanupAlert implements ExportCleanupAlertPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingExportCleanupAlert.class);

    @Override
    public void alert(ExportCleanupTask task, String errorMessage) {
        LOGGER.error("导出文件清理达到最大重试次数: jobId={}, userId={}, error={}",
                task.getJobId(), task.getUserId(), errorMessage);
    }
}
