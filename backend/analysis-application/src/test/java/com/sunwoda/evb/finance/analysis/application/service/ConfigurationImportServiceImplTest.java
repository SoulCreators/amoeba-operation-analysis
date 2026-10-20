package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportFileSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationImportServiceImplTest {
    @Test
    void storesConfigurationFileAndMovesTaskToValidating() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);

        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        ConfigurationImportTask uploaded = service.upload(created.getTaskNo(),
                new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertEquals(ImportStatus.VALIDATING, uploaded.getStatus());
        assertArrayEquals(new byte[]{1, 2, 3}, files.data.get(created.getTaskNo()));
    }

    @Test
    void restoresPreviousFileWhenFileSaveFails() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        files.data.put(created.getTaskNo(), new byte[]{4, 5, 6});
        files.previous = new ImportFileSnapshot("old.xlsx", new byte[]{4, 5, 6});
        RuntimeException failure = new IllegalStateException("file save failed");
        files.saveFailure = failure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.upload(created.getTaskNo(), new ByteArrayInputStream(new byte[]{1, 2, 3})));

        assertSame(failure, thrown);
        assertArrayEquals(new byte[]{4, 5, 6}, files.data.get(created.getTaskNo()));
        assertEquals(ImportStatus.CREATED, created.getStatus());
    }

    @Test
    void restoresPreviousFileWhenTaskSaveFailsAfterStatusMove() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        files.data.put(created.getTaskNo(), new byte[]{4, 5, 6});
        files.previous = new ImportFileSnapshot("old.xlsx", new byte[]{4, 5, 6});
        RuntimeException failure = new IllegalStateException("task save failed");
        tasks.saveFailure = failure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.upload(created.getTaskNo(), new ByteArrayInputStream(new byte[]{1, 2, 3})));

        assertSame(failure, thrown);
        assertArrayEquals(new byte[]{4, 5, 6}, files.data.get(created.getTaskNo()));
    }

    @Test
    void deletesNewFileWhenTaskSaveFailsWithoutPreviousFile() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        RuntimeException failure = new IllegalStateException("task save failed");
        tasks.saveFailure = failure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.upload(created.getTaskNo(), new ByteArrayInputStream(new byte[]{1, 2, 3})));

        assertSame(failure, thrown);
        org.junit.jupiter.api.Assertions.assertFalse(files.data.containsKey(created.getTaskNo()));
    }

    @Test
    void restoresPreviousFileWhenStatusMoveFails() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        created.moveTo(ImportStatus.CANCELLED);
        files.data.put(created.getTaskNo(), new byte[]{4, 5, 6});
        files.previous = new ImportFileSnapshot("old.xlsx", new byte[]{4, 5, 6});

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.upload(created.getTaskNo(), new ByteArrayInputStream(new byte[]{1, 2, 3})));

        assertEquals("已取消的配置导入不可恢复", thrown.getMessage());
        assertArrayEquals(new byte[]{4, 5, 6}, files.data.get(created.getTaskNo()));
    }

    @Test
    void preservesOriginalFailureWhenRestoreFails() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        ConfigurationImportService service = new ConfigurationImportServiceImpl(tasks, files);
        ConfigurationImportTask created = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", null, "U001");
        files.previous = new ImportFileSnapshot("old.xlsx", new byte[]{4, 5, 6});
        RuntimeException originalFailure = new IllegalStateException("task save failed");
        RuntimeException restoreFailure = new IllegalStateException("restore failed");
        tasks.saveFailure = originalFailure;
        files.restoreFailure = restoreFailure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.upload(created.getTaskNo(), new ByteArrayInputStream(new byte[]{1, 2, 3})));

        assertSame(originalFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(restoreFailure, thrown.getSuppressed()[0]);
    }

    private static class FakeTaskRepository implements ConfigurationImportTaskRepository {
        private final Map<String, ConfigurationImportTask> data = new HashMap<String, ConfigurationImportTask>();
        private RuntimeException saveFailure;

        @Override
        public ConfigurationImportTask save(ConfigurationImportTask task) {
            if (saveFailure != null) throw saveFailure;
            data.put(task.getTaskNo(), task);
            return task;
        }

        @Override
        public Optional<ConfigurationImportTask> findByTaskNo(String taskNo) {
            return Optional.ofNullable(data.get(taskNo));
        }
    }

    private static class FakeFileRepository implements ImportFileRepository {
        private final Map<String, byte[]> data = new HashMap<String, byte[]>();
        private ImportFileSnapshot previous;
        private RuntimeException saveFailure;
        private RuntimeException restoreFailure;

        @Override
        public void save(String taskNo, String fileName, byte[] content) {
            if (saveFailure != null) throw saveFailure;
            data.put(taskNo, content);
        }

        @Override
        public Optional<ImportFileSnapshot> snapshot(String taskNo) {
            return Optional.ofNullable(previous);
        }

        @Override
        public void restore(String taskNo, ImportFileSnapshot snapshot) {
            if (restoreFailure != null) throw restoreFailure;
            if (snapshot == null) data.remove(taskNo);
            else data.put(taskNo, snapshot.getContent());
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(data.get(taskNo));
        }
    }
}
