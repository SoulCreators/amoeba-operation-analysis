package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryAlertPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupPort;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.infrastructure.filesystem.FileSystemExportFileStorageAdapter;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisWorkerApplicationContextTest {

    @Test
    void defaultStartupDoesNotEnableDbOrFilesystemCleanup() {
        try (ConfigurableApplicationContext context = start()) {
            assertEquals("memory", context.getEnvironment().getProperty("spring.profiles.default"));
            assertFalse(context.getEnvironment().acceptsProfiles("db"));
            assertFalse(context.getEnvironment().acceptsProfiles("filesystem"));
            assertFalse(context.getBeansOfType(DataSource.class).size() > 0);
            assertFalse(context.getBeansOfType(ExportCleanupWorker.class).size() > 0);
            // 本次补充默认 memory profile 不启用审计端口及其 JDBC 技术实现的断言。
            assertFalse(context.getBeansOfType(AuditEventPort.class).size() > 0);
            assertFalse(context.getBeansOfType(JdbcAuditEventRepository.class).size() > 0);
            assertFalse(context.getBeansOfType(FileSystemExportFileStorageAdapter.class).size() > 0);
        }
    }

    @Test
    void explicitDbAndFilesystemProfilesEnableCleanupWiringFromEnvironmentProperties() throws Exception {
        Path storageRoot = Files.createTempDirectory("analysis-worker-storage-");
        try {
            System.setProperty("AMOEBA_DB_URL", "jdbc:postgresql://127.0.0.1:1/unused");
            System.setProperty("AMOEBA_DB_USERNAME", "test-user");
            System.setProperty("AMOEBA_DB_PASSWORD", "test-password");
            System.setProperty("ANALYSIS_STORAGE_ROOT", storageRoot.toString());

            try (ConfigurableApplicationContext context = start("db", "filesystem")) {
                assertTrue(context.getEnvironment().acceptsProfiles("db"));
                assertTrue(context.getEnvironment().acceptsProfiles("filesystem"));
                assertEquals("jdbc:postgresql://127.0.0.1:1/unused",
                        context.getEnvironment().getProperty("spring.datasource.url"));
                assertEquals(storageRoot.toString(),
                        context.getEnvironment().getProperty("analysis.storage.root"));
                assertNotNull(context.getBean(DataSource.class));
                // 本次补充 db profile 下审计端口与 JDBC 审计仓储均完成技术接线的断言。
                assertNotNull(context.getBean(AuditEventPort.class));
                assertTrue(context.getBeansOfType(AuditEventPort.class).keySet().stream()
                        .anyMatch(type -> type.equals(JdbcAuditEventRepository.class.getName())));
                assertTrue(context.getBeansOfType(ExportCleanupPort.class).size() > 0);
                assertTrue(context.getBeansOfType(ExportFileStoragePort.class).size() > 0);
                assertNotNull(context.getBean(ExportCleanupWorker.class));
                assertNotNull(context.getBean(ExportFileStoragePort.class));
                // 本次回归测试：db profile 必须完成计算服务、重试执行器和两个 Worker 的实际接线。
                assertNotNull(context.getBean(CalculationService.class));
                assertNotNull(context.getBean(CalculationRetryExecutor.class));
                // 本次新增回归断言：db profile 必须接入计算重试人工接管告警适配器。
                assertNotNull(context.getBean(CalculationRetryAlertPort.class));
                // 本次修复：按 Bean 实例类型校验适配器，避免 Spring 默认 Bean 名称与类全限定名不一致造成误报。
                assertTrue(context.getBeansOfType(CalculationRetryAlertPort.class).values().stream()
                        .anyMatch(LoggingCalculationRetryAlert.class::isInstance));
                assertNotNull(context.getBean(CalculationJobRetryWorker.class));
                assertNotNull(context.getBean(CalculationRetryScheduledWorker.class));
            }
        } finally {
            System.clearProperty("AMOEBA_DB_URL");
            System.clearProperty("AMOEBA_DB_USERNAME");
            System.clearProperty("AMOEBA_DB_PASSWORD");
            System.clearProperty("ANALYSIS_STORAGE_ROOT");
            Files.deleteIfExists(storageRoot);
        }
    }

    private ConfigurableApplicationContext start(String... profiles) {
        return new SpringApplicationBuilder(AnalysisWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .properties("spring.main.web-application-type=none",
                        "analysis.worker.cleanup.scheduling-enabled=false")
                .run();
    }
}
