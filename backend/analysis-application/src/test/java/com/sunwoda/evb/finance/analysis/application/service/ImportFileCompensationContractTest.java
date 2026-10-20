package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactSourceReader;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies file compensation around both transactional import services. */
class ImportFileCompensationContractTest {
    private static final byte[] NEW_CONTENT = new byte[]{9, 8, 7};

    @Test
    void factImportRestoresPreviousFileWhenDatabasePipelineFails() throws Exception {
        ImportFileRepository files = mock(ImportFileRepository.class);
        ImportFileSnapshot previous = new ImportFileSnapshot("old.xlsx", new byte[]{1, 2});
        when(files.snapshot("IMP-FILE-001")).thenReturn(Optional.of(previous));

        ImportTask task = importTask("IMP-FILE-001");
        ImportTaskRepository tasks = mock(ImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        PnlFactSourceWriter sourceWriter = mock(PnlFactSourceWriter.class);
        RuntimeException sourceFailure = new IllegalStateException("trace write failed");
        doThrow(sourceFailure).when(sourceWriter)
                .replace(any(AnalysisBatch.class), eq(task), anyList());

        ImportTaskServiceImpl service = factService(tasks, files, sourceWriter);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(NEW_CONTENT)));

        assertSame(sourceFailure, thrown);
        verify(files).restore(task.getTaskNo(), previous);
        verify(files, never()).delete(task.getTaskNo());
    }

    @Test
    void configurationImportKeepsInvalidFileAndDoesNotCompensate() throws Exception {
        ImportFileRepository files = mock(ImportFileRepository.class);
        ConfigurationImportTask task = configurationTask("CFG-FILE-001");
        ConfigurationImportTaskRepository tasks = mock(ConfigurationImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.empty());

        ConfigurationImportServiceImpl service = configurationService(tasks, files,
                new ImportValidationResult(task.getTaskNo(), ImportStatus.INVALID, 0,
                        Collections.<String>emptyList(), Collections.<ImportIssue>emptyList()),
                null);

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(NEW_CONTENT));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        verify(files).save(task.getTaskNo(), task.getFileName(), NEW_CONTENT);
        verify(files, never()).restore(eq(task.getTaskNo()), isNull());
        verify(files, never()).delete(task.getTaskNo());
    }

    @Test
    void configurationImportPreservesOriginalFailureWhenCompensationFails() throws Exception {
        ImportFileRepository files = mock(ImportFileRepository.class);
        ConfigurationImportTask task = configurationTask("CFG-FILE-002");
        ConfigurationImportTaskRepository tasks = mock(ConfigurationImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);
        when(files.snapshot(task.getTaskNo())).thenReturn(Optional.empty());
        RuntimeException cleanupFailure = new IllegalStateException("restore failed");
        doThrow(cleanupFailure).when(files).restore(task.getTaskNo(), null);
        RuntimeException validationFailure = new IllegalArgumentException("validator failed");

        ConfigurationImportServiceImpl service = configurationService(tasks, files,
                null, validationFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(NEW_CONTENT)));

        assertSame(validationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(cleanupFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void factImportRestoresAbsentFileByDeletingNewFileOnFailure() throws Exception {
        ImportFileRepository files = mock(ImportFileRepository.class);
        when(files.snapshot("IMP-FILE-002")).thenReturn(Optional.empty());
        ImportTask task = importTask("IMP-FILE-002");
        ImportTaskRepository tasks = mock(ImportTaskRepository.class);
        when(tasks.findByTaskNo(task.getTaskNo())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);
        PnlFactSourceWriter sourceWriter = mock(PnlFactSourceWriter.class);
        doThrow(new IllegalStateException("trace write failed")).when(sourceWriter)
                .replace(any(AnalysisBatch.class), eq(task), anyList());

        ImportTaskServiceImpl service = factService(tasks, files, sourceWriter);

        assertThrows(RuntimeException.class,
                () -> service.validate(task.getTaskNo(), new ByteArrayInputStream(NEW_CONTENT)));

        verify(files).restore(task.getTaskNo(), null);
    }

    private ImportTaskServiceImpl factService(ImportTaskRepository tasks,
                                               ImportFileRepository files,
                                               PnlFactSourceWriter sourceWriter) throws Exception {
        AnalysisBatchRepository batches = mock(AnalysisBatchRepository.class);
        when(batches.findByBatchNo("B-FILE-001")).thenReturn(Optional.of(batch("B-FILE-001")));
        DatasetCatalog catalog = mock(DatasetCatalog.class);
        when(catalog.require("BASE_ACT_INC_COST", AnalysisPerspective.BASE))
                .thenReturn(new DatasetDefinition("BASE_ACT_INC_COST", "actual", "BASE", true,
                        Collections.singletonList(AnalysisPerspective.BASE)));
        TemplateValidator validator = mock(TemplateValidator.class);
        when(validator.validate(eq("IMP-FILE-001"), org.mockito.ArgumentMatchers.any(DatasetDefinition.class),
                org.mockito.ArgumentMatchers.any(InputStream.class)))
                .thenReturn(validResult("IMP-FILE-001"));
        when(validator.validate(eq("IMP-FILE-002"), org.mockito.ArgumentMatchers.any(DatasetDefinition.class),
                org.mockito.ArgumentMatchers.any(InputStream.class)))
                .thenReturn(validResult("IMP-FILE-002"));
        ImportTaskServiceImpl service = new ImportTaskServiceImpl(batches, tasks, catalog, validator,
                mock(ImportIssueRepository.class), files);
        set(service, "factReader", (PnlFactTaskReader) (batch, task) -> Collections.<PnlFact>emptyList());
        set(service, "sourceReader", (PnlFactSourceReader) (batch, task) -> Collections.<PnlFactSource>emptyList());
        set(service, "factWriter", mock(PnlFactWriter.class));
        set(service, "sourceWriter", sourceWriter);
        return service;
    }

    private ConfigurationImportServiceImpl configurationService(
            ConfigurationImportTaskRepository tasks, ImportFileRepository files,
            ImportValidationResult result, RuntimeException failure) throws Exception {
        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationTemplateValidator validator = mock(ConfigurationTemplateValidator.class);
        if (failure == null) {
            when(validator.validate(eq("CFG-FILE-001"), eq(ConfigurationImportType.METRIC_DEFINITION),
                    org.mockito.ArgumentMatchers.any(InputStream.class))).thenReturn(result);
        } else {
            when(validator.validate(eq("CFG-FILE-002"), eq(ConfigurationImportType.METRIC_DEFINITION),
                    org.mockito.ArgumentMatchers.any(InputStream.class))).thenThrow(failure);
        }
        set(service, "validator", validator);
        set(service, "issueRepository", mock(ConfigurationImportIssueRepository.class));
        if (failure == null) {
            set(service, "applier", mock(ConfigurationImportApplier.class));
        }
        return service;
    }

    private static ImportValidationResult validResult(String taskNo) {
        return new ImportValidationResult(taskNo, ImportStatus.VALID, 0,
                Collections.<String>emptyList(), Collections.<ImportIssue>emptyList());
    }

    private static AnalysisBatch batch(String batchNo) {
        return new AnalysisBatch(1L, batchNo, "2026-10", AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private static ImportTask importTask(String taskNo) {
        return new ImportTask(1L, taskNo, "B-FILE-001", "BASE_ACT_INC_COST",
                "new.xlsx", "checksum", "tester", LocalDateTime.now());
    }

    private static ConfigurationImportTask configurationTask(String taskNo) {
        return new ConfigurationImportTask(1L, taskNo, ConfigurationImportType.METRIC_DEFINITION,
                "config.xlsx", "checksum", "tester", LocalDateTime.now());
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
