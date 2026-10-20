package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = "com.sunwoda.evb.finance.amoeba.worker")
@Import({
        WorkerDataSourceConfiguration.class,
        // 本次补齐 db + filesystem 场景的 JDBC 导出任务仓储显式接线，避免 Worker 回退到不可用占位适配器。
        com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportTaskRepository.class,
        com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportCleanupRepository.class,
        com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter.class
})
public class AnalysisWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
