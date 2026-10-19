package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ImportTaskServiceTransactionWiringTest.TransactionConfig.class)
class ImportTaskServiceTransactionWiringTest {
    @javax.annotation.Resource
    private ImportTaskServiceImpl service;

    @javax.annotation.Resource
    private DataSource dataSource;

    @javax.annotation.Resource
    private Connection connection;

    @javax.annotation.Resource
    private RecordingFactWriter factWriter;

    @javax.annotation.Resource
    private RecordingSourceWriter sourceWriter;

    @javax.annotation.Resource
    private FakeImportTaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.save(task("IMP-TX", "B-TX"));
        factWriter.reset();
        sourceWriter.reset();
        clearInvocations(dataSource, connection);
    }

    @Test
    void validValidationUsesOneConnectionAndCommits() throws Exception {
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isCglibProxy(service));

        ImportValidationResult result = service.validate(
                "IMP-TX", new ByteArrayInputStream(new byte[]{1}));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertSame(connection, factWriter.connection);
        assertSame(connection, sourceWriter.connection);
        verify(dataSource, times(1)).getConnection();
        verify(connection, times(1)).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void sourceWriterFailureRollsBackTheTransaction() throws Exception {
        sourceWriter.fail = true;

        assertThrows(RuntimeException.class, () -> service.validate(
                "IMP-TX", new ByteArrayInputStream(new byte[]{1})));

        assertSame(connection, factWriter.connection);
        assertSame(connection, sourceWriter.connection);
        verify(dataSource, times(1)).getConnection();
        verify(connection, never()).commit();
        verify(connection, times(1)).rollback();
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
        ImportTaskServiceImpl importTaskService(AnalysisBatchRepository batches,
                                                ImportTaskRepository tasks,
                                                DatasetCatalog catalog,
                                                TemplateValidator validator,
                                                ImportIssueRepository issues,
                                                ImportFileRepository files) {
            return new ImportTaskServiceImpl(batches, tasks, catalog, validator, issues, files);
        }

        @Bean
        FakeBatchRepository batchRepository() {
            FakeBatchRepository repository = new FakeBatchRepository();
            repository.save(batch("B-TX"));
            return repository;
        }

        @Bean
        FakeImportTaskRepository importTaskRepository() {
            return new FakeImportTaskRepository();
        }

        @Bean
        DatasetCatalog datasetCatalog() {
            return new FakeDatasetCatalog();
        }

        @Bean
        TemplateValidator templateValidator() {
            return new ValidTemplateValidator();
        }

        @Bean
        ImportIssueRepository importIssueRepository() {
            return new FakeImportIssueRepository();
        }

        @Bean
        ImportFileRepository importFileRepository() {
            return new FakeImportFileRepository();
        }

        @Bean
        PnlFactTaskReader factReader() {
            return new EmptyFactReader();
        }

        @Bean
        PnlFactSourceReader sourceReader() {
            return new EmptySourceReader();
        }

        @Bean
        RecordingFactWriter factWriter(DataSource dataSource) {
            return new RecordingFactWriter(dataSource);
        }

        @Bean
        RecordingSourceWriter sourceWriter(DataSource dataSource) {
            return new RecordingSourceWriter(dataSource);
        }
    }

    private static AnalysisBatch batch(String batchNo) {
        return new AnalysisBatch(1L, batchNo, "2026-09", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private static ImportTask task(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "BASE_ACT_INC_COST",
                "actual.xlsx", "sha256", "tester", LocalDateTime.now());
    }

    private static class EmptyFactReader implements PnlFactTaskReader {
        @Override
        public List<PnlFact> read(AnalysisBatch batch, ImportTask task) {
            return Collections.emptyList();
        }
    }

    private static class EmptySourceReader implements PnlFactSourceReader {
        @Override
        public List<PnlFactSource> readSources(AnalysisBatch batch, ImportTask task) {
            return Collections.emptyList();
        }
    }

    private static class RecordingFactWriter implements PnlFactWriter {
        private final DataSource dataSource;
        private Connection connection;

        private RecordingFactWriter(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFact> facts) {
            connection = DataSourceUtils.getConnection(dataSource);
        }

        private void reset() {
            connection = null;
        }
    }

    private static class RecordingSourceWriter implements PnlFactSourceWriter {
        private final DataSource dataSource;
        private Connection connection;
        private boolean fail;

        private RecordingSourceWriter(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources) {
            connection = DataSourceUtils.getConnection(dataSource);
            if (fail) {
                throw new RuntimeException("source write failed");
            }
        }

        @Override
        public List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
                String batchNo, AnalysisPerspective perspective, String taskNo) {
            return Collections.emptyList();
        }

        private void reset() {
            connection = null;
            fail = false;
        }
    }

    private static class FakeDatasetCatalog implements DatasetCatalog {
        @Override
        public List<DatasetDefinition> list(AnalysisPerspective perspective) {
            return Collections.emptyList();
        }

        @Override
        public DatasetDefinition require(String datasetCode, AnalysisPerspective perspective) {
            return new DatasetDefinition(datasetCode, "基地实际收入成本明细", "MONTHLY", true,
                    Collections.singletonList(AnalysisPerspective.BASE));
        }
    }

    private static class ValidTemplateValidator implements TemplateValidator {
        @Override
        public ImportValidationResult validate(String taskNo, DatasetDefinition definition,
                                               InputStream inputStream) {
            return new ImportValidationResult(taskNo, ImportStatus.VALID, 0,
                    Collections.<String>emptyList(), Collections.<ImportIssue>emptyList());
        }
    }

    private static class FakeImportIssueRepository implements ImportIssueRepository {
        @Override
        public void replace(String taskNo, List<ImportIssue> issues) {
        }

        @Override
        public List<ImportIssue> findByTaskNo(String taskNo) {
            return Collections.emptyList();
        }
    }

    private static class FakeImportFileRepository implements ImportFileRepository {
        @Override
        public void save(String taskNo, String fileName, byte[] content) {
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private static class FakeBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> data = new HashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            data.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(data.get(batchNo));
        }
    }

    private static class FakeImportTaskRepository implements ImportTaskRepository {
        private final Map<String, ImportTask> data = new HashMap<String, ImportTask>();

        @Override
        public ImportTask save(ImportTask task) {
            data.put(task.getTaskNo(), task);
            return task;
        }

        @Override
        public Optional<ImportTask> findByTaskNo(String taskNo) {
            return Optional.ofNullable(data.get(taskNo));
        }

        @Override
        public boolean existsByBatchAndDataset(String batchNo, String datasetCode) {
            return false;
        }

        @Override
        public List<ImportTask> findByBatchNo(String batchNo) {
            return Collections.emptyList();
        }
    }
}
