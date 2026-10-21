package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAuditEventRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationLifecycleEventDeliveryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationLifecycleEventDeliveryApplicationAdapter;
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
        // 本次补齐 db profile 下审计端口到 JDBC 审计仓储的显式技术接线。
        JdbcAuditEventRepository.class,
        // 本次接线：把 Outbox 原子领取/租约/CAS 适配器显式导入 Worker，保持 db profile 才可用。
        JdbcCalculationLifecycleEventDeliveryRepository.class,
        // 本次接线：将 PostgreSQL 领取端口桥接为 application port，允许 Worker 安全创建。
        JdbcCalculationLifecycleEventDeliveryApplicationAdapter.class,
        com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter.class
})
public class AnalysisWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalysisWorkerApplication.class, args);
    }
}
