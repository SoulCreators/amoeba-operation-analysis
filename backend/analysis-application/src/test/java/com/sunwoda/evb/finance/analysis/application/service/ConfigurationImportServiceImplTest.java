package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
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

    private static class FakeTaskRepository implements ConfigurationImportTaskRepository {
        private final Map<String, ConfigurationImportTask> data = new HashMap<String, ConfigurationImportTask>();

        @Override
        public ConfigurationImportTask save(ConfigurationImportTask task) {
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

        @Override
        public void save(String taskNo, String fileName, byte[] content) {
            data.put(taskNo, content);
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(data.get(taskNo));
        }
    }
}
