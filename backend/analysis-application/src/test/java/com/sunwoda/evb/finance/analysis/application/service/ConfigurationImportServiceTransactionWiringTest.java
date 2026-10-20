package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies the configuration import validate operation is transaction-advised. */
@SpringJUnitConfig(ConfigurationImportServiceTransactionWiringTest.TransactionConfig.class)
class ConfigurationImportServiceTransactionWiringTest {
    @Resource
    private ConfigurationImportServiceImpl service;

    @Resource
    private DataSource dataSource;

    @Resource
    private Connection connection;

    @Resource
    private RecordingTaskRepository tasks;

    @Resource
    private RecordingApplier applier;

    @BeforeEach
    void setUp() throws Exception {
        reset(dataSource, connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        tasks.reset();
        applier.fail = false;
    }

    @Test
    void validateUsesTransactionProxyAndCommits() throws Exception {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));

        ImportValidationResult result = service.validate(
                "CFG-TX", new ByteArrayInputStream(new byte[]{1}));

        assertEquals(ImportStatus.VALID, result.getStatus());
        verify(dataSource).getConnection();
        verify(connection).commit();
        assertEquals(ImportStatus.VALID, tasks.task.getStatus());
    }

    @Test
    void applierFailureRollsBackAndDoesNotPersistFinalStatus() throws Exception {
        applier.fail = true;

        assertThrows(IllegalStateException.class, () -> service.validate(
                "CFG-TX", new ByteArrayInputStream(new byte[]{1})));

        verify(dataSource).getConnection();
        verify(connection).rollback();
        assertEquals(ImportStatus.CREATED, tasks.task.getStatus());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {
        @Bean
        Connection connection() throws Exception {
            return mock(Connection.class);
        }

        @Bean
        DataSource dataSource(Connection connection) throws Exception {
            return mock(DataSource.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ConfigurationImportServiceImpl configurationImportService(
                ConfigurationImportTaskRepository tasks,
                ImportFileRepository files) {
            return new ConfigurationImportServiceImpl(tasks, files);
        }

        @Bean
        RecordingTaskRepository configurationImportTaskRepository() {
            return new RecordingTaskRepository();
        }

        @Bean
        ImportFileRepository importFileRepository() {
            return new NoOpFileRepository();
        }

        @Bean
        ConfigurationImportIssueRepository configurationImportIssueRepository() {
            return new NoOpIssueRepository();
        }

        @Bean
        ConfigurationTemplateValidator configurationTemplateValidator() {
            return (taskNo, type, input) -> new ImportValidationResult(taskNo,
                    ImportStatus.VALID, 1, Collections.<String>emptyList(),
                    Collections.<ImportIssue>emptyList());
        }

        @Bean
        RecordingApplier configurationImportApplier() {
            return new RecordingApplier();
        }
    }

    static final class RecordingTaskRepository implements ConfigurationImportTaskRepository {
        private ConfigurationImportTask task;

        RecordingTaskRepository() {
            reset();
        }

        void reset() {
            task = new ConfigurationImportTask(1L, "CFG-TX",
                    ConfigurationImportType.METRIC_DEFINITION, "config.xlsx",
                    "checksum", "tester", LocalDateTime.now());
        }

        @Override
        public ConfigurationImportTask save(ConfigurationImportTask value) {
            task = value;
            return value;
        }

        @Override
        public Optional<ConfigurationImportTask> findByTaskNo(String taskNo) {
            return task.getTaskNo().equals(taskNo) ? Optional.of(task) : Optional.empty();
        }
    }

    static final class RecordingApplier implements ConfigurationImportApplier {
        private boolean fail;

        @Override
        public ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream input) {
            if (fail) throw new IllegalStateException("applier failed");
            return new ConfigurationApplyResult(1, Collections.<ImportIssue>emptyList());
        }
    }

    static final class NoOpFileRepository implements ImportFileRepository {
        @Override
        public void save(String taskNo, String fileName, byte[] content) {
        }

        @Override
        public InputStream open(String taskNo) throws IOException {
            return new ByteArrayInputStream(new byte[]{1});
        }
    }

    static final class NoOpIssueRepository implements ConfigurationImportIssueRepository {
        @Override
        public void replace(String taskNo, java.util.List<ImportIssue> issues) {
        }

        @Override
        public java.util.List<ImportIssue> findByTaskNo(String taskNo) {
            return Collections.emptyList();
        }
    }
}
