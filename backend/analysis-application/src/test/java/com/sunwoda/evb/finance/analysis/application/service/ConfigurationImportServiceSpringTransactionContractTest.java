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
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 固化配置导入服务当前 Spring 事务代理、直接调用边界和异常传播契约。
 * 本测试只验证现有 @Transactional 语义，不宣称真实数据库回滚，也不引入业务状态。
 */
@SpringJUnitConfig(ConfigurationImportServiceSpringTransactionContractTest.TransactionConfig.class)
class ConfigurationImportServiceSpringTransactionContractTest {
    @Resource
    private ConfigurationImportService service;

    @Resource
    private DataSource dataSource;

    @Resource
    private Connection connection;

    @Resource
    private RecordingTaskRepository tasks;

    @Resource
    private RecordingValidator validator;

    @Resource
    private RecordingApplier applier;

    @BeforeEach
    void setUp() {
        clearInvocations(dataSource, connection);
        tasks.reset();
        validator.reset();
        applier.reset();
    }

    @Test
    void interfaceInvocationEntersCglibProxyBeforeCollaborators() throws Exception {
        // 通过应用服务接口调用，验证 Spring 代理在 validate 协作者执行前开启事务。
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));

        ImportValidationResult result = service.validate(
                "CFG-SPRING-TX", new ByteArrayInputStream(new byte[]{1}));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertTrue(validator.transactionActiveAtInvocation);
        verify(dataSource).getConnection();
        verify(connection).commit();
    }

    @Test
    void directTargetInvocationBypassesSpringTransactionAdvice() throws Exception {
        // 直接调用目标对象只作为边界契约，证明事务能力来自 Spring 代理而非方法自调用。
        ConfigurationImportServiceImpl target = AopTestUtils.getTargetObject(service);

        ImportValidationResult result = target.validate(
                "CFG-SPRING-TX", new ByteArrayInputStream(new byte[]{1}));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertFalse(validator.transactionActiveAtInvocation);
        verify(dataSource, never()).getConnection();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void proxyPropagatesOriginalRuntimeExceptionWithoutNewFailureState() throws Exception {
        // 通过代理调用时，协作者运行时异常必须原样向上抛出，不转换为新的业务状态。
        applier.failure = new IllegalStateException("配置应用失败");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.validate("CFG-SPRING-TX", new ByteArrayInputStream(new byte[]{1})));

        assertSame(applier.failure, thrown);
        assertTrue(validator.transactionActiveAtInvocation);
        verify(connection).rollback();
        assertEquals(ImportStatus.CREATED, tasks.task.getStatus());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {
        @Bean
        Connection connection() throws Exception {
            Connection connection = mock(Connection.class);
            when(connection.getAutoCommit()).thenReturn(true);
            return connection;
        }

        @Bean
        DataSource dataSource(Connection connection) throws Exception {
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(connection);
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ConfigurationImportServiceImpl configurationImportService(
                ConfigurationImportTaskRepository tasks, ImportFileRepository files) {
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
        RecordingValidator configurationTemplateValidator() {
            return new RecordingValidator();
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
            task = new ConfigurationImportTask(1L, "CFG-SPRING-TX",
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

    static final class RecordingValidator implements ConfigurationTemplateValidator {
        private boolean transactionActiveAtInvocation;

        void reset() {
            transactionActiveAtInvocation = false;
        }

        @Override
        public ImportValidationResult validate(String taskNo, ConfigurationImportType type,
                                               InputStream input) {
            transactionActiveAtInvocation = TransactionSynchronizationManager.isActualTransactionActive();
            return new ImportValidationResult(taskNo, ImportStatus.VALID, 1,
                    Collections.<String>emptyList(), Collections.<ImportIssue>emptyList());
        }
    }

    static final class RecordingApplier implements ConfigurationImportApplier {
        private RuntimeException failure;

        void reset() {
            failure = null;
        }

        @Override
        public ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream input) {
            if (failure != null) throw failure;
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
