package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * confirm/publish Spring 事务接线契约。
 *
 * <p>本次新增测试：验证接口调用经过 Spring 代理、批次和结果协作者执行时事务已开启，
 * 以及结果保存异常触发 JDBC 事务回滚；真实 PostgreSQL 数据行恢复由环境契约另行验证。</p>
 */
@SpringJUnitConfig(CalculationServiceTransactionWiringTest.TransactionConfig.class)
class CalculationServiceTransactionWiringTest {
    @Resource
    private CalculationService service;
    @Resource
    private RecordingBatchRepository batches;
    @Resource
    private RecordingResultRepository results;
    @Resource
    private Connection connection;

    @BeforeEach
    void setUp() {
        clearInvocations(connection);
        batches.reset();
        results.reset();
    }

    @Test
    void confirmRunsThroughProxyAndCommitsAfterBothRepositories() throws Exception {
        CalculationResult result = service.confirm(batches.batch.getBatchNo(), "finance-confirm");

        assertSame(CalculationResultStatus.CONFIRMED, result.getStatus());
        assertTrue(batches.transactionActiveAtSave);
        assertTrue(results.transactionActiveAtSave);
        verify(connection).commit();
    }

    @Test
    void publishResultFailureRollsBackTheSpringTransaction() throws Exception {
        batches.batch.moveTo(BatchStatus.CONFIRMED);
        results.result = new CalculationResult("TX-WIRING-BATCH", AnalysisPerspective.BASE,
                Collections.emptyList(), "TX-WIRING-R1", CalculationResultStatus.CONFIRMED,
                "finance-confirm", null, LocalDateTime.now(), LocalDateTime.now(), null,
                Collections.emptyList());
        results.failure = new IllegalStateException("发布结果保存失败");

        assertTrue(AopUtils.isAopProxy(service));
        assertThrows(IllegalStateException.class,
                () -> service.publish(batches.batch.getBatchNo(), "finance-publish"));

        assertTrue(batches.transactionActiveAtSave);
        assertTrue(results.transactionActiveAtSave);
        verify(connection).rollback();
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
        CalculationServiceImpl calculationService(AnalysisBatchRepository batches,
                                                   PnlFactProvider facts,
                                                   PermissionService permissions,
                                                   CalculationResultRepository results) {
            return new CalculationServiceImpl(batches, facts, permissions, results);
        }

        @Bean
        RecordingBatchRepository analysisBatchRepository() {
            return new RecordingBatchRepository();
        }

        @Bean
        RecordingResultRepository calculationResultRepository() {
            return new RecordingResultRepository();
        }

        @Bean
        PnlFactProvider pnlFactProvider() {
            return batch -> Collections.emptyList();
        }

        @Bean
        PermissionService permissionService() {
            UserScopeRepository scopes = mock(UserScopeRepository.class);
            when(scopes.findByUserAndPerspective("finance-confirm", AnalysisPerspective.BASE))
                    .thenReturn(Collections.singletonList(scope("finance-confirm")));
            when(scopes.findByUserAndPerspective("finance-publish", AnalysisPerspective.BASE))
                    .thenReturn(Collections.singletonList(scope("finance-publish")));
            return new PermissionServiceImpl(scopes);
        }

        private static UserScope scope(String userId) {
            return new UserScope(userId, "HQ_FINANCE", AnalysisPerspective.BASE,
                    "ALL", true, true, true, true);
        }
    }

    static final class RecordingBatchRepository implements AnalysisBatchRepository {
        private AnalysisBatch batch;
        private boolean transactionActiveAtSave;

        RecordingBatchRepository() { reset(); }

        void reset() {
            batch = new AnalysisBatch(1L, "TX-WIRING-BATCH", "2026-10",
                    AnalysisPerspective.BASE, BatchStatus.CALCULATED, "tx-test", LocalDateTime.now());
            transactionActiveAtSave = false;
        }

        @Override
        public AnalysisBatch save(AnalysisBatch value) {
            transactionActiveAtSave = TransactionSynchronizationManager.isActualTransactionActive();
            batch = value;
            return value;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return batch.getBatchNo().equals(batchNo) ? Optional.of(batch) : Optional.empty();
        }
    }

    static final class RecordingResultRepository implements CalculationResultRepository {
        private CalculationResult result;
        private RuntimeException failure;
        private boolean transactionActiveAtSave;

        RecordingResultRepository() { reset(); }

        void reset() {
            result = new CalculationResult("TX-WIRING-BATCH", AnalysisPerspective.BASE,
                    Collections.emptyList(), "TX-WIRING-R1", CalculationResultStatus.CALCULATED,
                    null, null, LocalDateTime.now(), null, null, Collections.emptyList());
            failure = null;
            transactionActiveAtSave = false;
        }

        @Override
        public CalculationResult save(CalculationResult value) {
            transactionActiveAtSave = TransactionSynchronizationManager.isActualTransactionActive();
            if (failure != null) throw failure;
            result = value;
            return value;
        }

        @Override
        public Optional<CalculationResult> findByBatchNo(String batchNo) {
            return result.getBatchNo().equals(batchNo) ? Optional.of(result) : Optional.empty();
        }
    }
}
