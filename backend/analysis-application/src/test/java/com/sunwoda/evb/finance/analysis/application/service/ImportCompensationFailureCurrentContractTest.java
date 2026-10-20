package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入补偿失败的当前行为契约。
 * 只记录现有 suppressed exception 行为，不引入持久化、重试、告警或扫描语义。
 */
class ImportCompensationFailureCurrentContractTest {
    private static final byte[] CONTENT = new byte[]{9, 8, 7};

    @Test
    void factValidateKeepsOriginalErrorWhenRestoreFails() throws Exception {
        ImportFileRepository files = mock(ImportFileRepository.class);
        ImportTask task = importTask("IMP-TECH-001", "B-TECH-001");
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.<ImportFileSnapshot>empty());
        RuntimeException restoreFailure = new IllegalStateException("fact restore failed");
        doThrow(restoreFailure).when(files).restore(task.getTaskNo(), null);

        AssertionError originalFailure = new AssertionError("fact source write failed");
        PnlFactSourceWriter sourceWriter = mock(PnlFactSourceWriter.class);
        doThrow(originalFailure).when(sourceWriter)
                .replace(any(AnalysisBatch.class), eq(task), anyList());

        ImportTaskServiceImpl service = factService(task, files, sourceWriter,
                validResult(task.getTaskNo()));

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(CONTENT)));

        assertSame(originalFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(restoreFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void configurationUploadKeepsOriginalErrorWhenRestoreFails() throws Exception {
        ConfigurationImportTask task = configurationTask("CFG-TECH-001");
        ImportFileRepository files = mock(ImportFileRepository.class);
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.<ImportFileSnapshot>empty());
        RuntimeException restoreFailure = new IllegalStateException("upload restore failed");
        doThrow(restoreFailure).when(files).restore(task.getTaskNo(), null);

        ConfigurationImportTaskRepository tasks = mock(ConfigurationImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        AssertionError originalFailure = new AssertionError("configuration task save failed");
        doThrow(originalFailure).when(tasks).save(task);

        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> service.upload(task.getTaskNo(), new ByteArrayInputStream(CONTENT)));

        assertSame(originalFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(restoreFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void configurationValidateKeepsOriginalErrorWhenRestoreFails() throws Exception {
        ConfigurationImportTask task = configurationTask("CFG-TECH-002");
        ImportFileRepository files = mock(ImportFileRepository.class);
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.<ImportFileSnapshot>empty());
        RuntimeException restoreFailure = new IllegalStateException("validation restore failed");
        doThrow(restoreFailure).when(files).restore(task.getTaskNo(), null);

        ConfigurationImportTaskRepository tasks = mock(ConfigurationImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        AssertionError originalFailure = new AssertionError("configuration validator failed");
        ConfigurationTemplateValidator validator = mock(ConfigurationTemplateValidator.class);
        when(validator.validate(eq(task.getTaskNo()), eq(task.getType()), any(InputStream.class)))
                .thenThrow(originalFailure);

        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        set(service, "validator", validator);
        set(service, "issueRepository", mock(ConfigurationImportIssueRepository.class));

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(CONTENT)));

        assertSame(originalFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(restoreFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void factValidateKeepsInvalidFileAndDoesNotCompensate() throws Exception {
        ImportTask task = importTask("IMP-TECH-002", "B-TECH-002");
        ImportFileRepository files = mock(ImportFileRepository.class);
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.<ImportFileSnapshot>empty());

        ImportTaskRepository tasks = mock(ImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        ImportValidationResult invalid = new ImportValidationResult(task.getTaskNo(),
                ImportStatus.INVALID, 1, Collections.<String>emptyList(),
                Collections.<ImportIssue>emptyList());
        ImportTaskServiceImpl service = factService(task, files, mock(PnlFactSourceWriter.class), invalid);

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(CONTENT));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        verify(files).save(task.getTaskNo(), task.getFileName(), CONTENT);
        verify(files, never()).restore(eq(task.getTaskNo()), isNull());
        verify(files, never()).delete(task.getTaskNo());
    }

    private ImportTaskServiceImpl factService(ImportTask task, ImportFileRepository files,
                                              PnlFactSourceWriter sourceWriter,
                                              ImportValidationResult validationResult) throws Exception {
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        when(batches.findByBatchNo(task.getBatchNo())).thenReturn(Optional.of(batch(task.getBatchNo())));
        ImportTaskRepository tasks = mock(ImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        DatasetCatalog catalog = mock(DatasetCatalog.class);
        when(catalog.require(task.getDatasetCode(), AnalysisPerspective.BASE))
                .thenReturn(new DatasetDefinition(task.getDatasetCode(), "actual", "BASE", true,
                        Collections.singletonList(AnalysisPerspective.BASE)));
        TemplateValidator validator = mock(TemplateValidator.class);
        when(validator.validate(eq(task.getTaskNo()), any(DatasetDefinition.class), any(InputStream.class)))
                .thenReturn(validationResult);

        ImportTaskServiceImpl service = new ImportTaskServiceImpl(batches, tasks, catalog,
                validator, mock(ImportIssueRepository.class), files);
        set(service, "factReader", (PnlFactTaskReader) (batch, currentTask) -> Collections.emptyList());
        set(service, "sourceReader", (PnlFactSourceReader) (batch, currentTask) -> Collections.emptyList());
        set(service, "factWriter", mock(PnlFactWriter.class));
        set(service, "sourceWriter", sourceWriter);
        return service;
    }

    private static AnalysisBatch batch(String batchNo) {
        return new AnalysisBatch(1L, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private static ImportTask importTask(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "BASE_ACT_INC_COST",
                "actual.xlsx", "checksum", "tester", LocalDateTime.now());
    }

    private static ConfigurationImportTask configurationTask(String taskNo) {
        return new ConfigurationImportTask(1L, taskNo, ConfigurationImportType.METRIC_DEFINITION,
                "config.xlsx", "checksum", "tester", LocalDateTime.now());
    }

    private static ImportValidationResult validResult(String taskNo) {
        return new ImportValidationResult(taskNo, ImportStatus.VALID, 0,
                Collections.<String>emptyList(), Collections.<ImportIssue>emptyList());
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
