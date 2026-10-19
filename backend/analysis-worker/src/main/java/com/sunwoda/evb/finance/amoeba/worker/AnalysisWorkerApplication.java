package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = "com.sunwoda.evb.finance.amoeba.worker")
@Import({
        WorkerDataSourceConfiguration.class,
        com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcExportCleanupRepository.class,
        com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter.class
})
public class AnalysisWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
