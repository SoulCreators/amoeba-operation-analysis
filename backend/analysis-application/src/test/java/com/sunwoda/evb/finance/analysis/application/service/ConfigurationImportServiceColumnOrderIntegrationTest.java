package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the application-service boundary with in-memory ports.
 *
 * <p>The infrastructure module owns the POI workbook parsing tests. This
 * application-module test deliberately uses a small header-based payload so
 * it can prove the Validator -> Service -> Applier wiring without introducing
 * a dependency from the application module back to infrastructure.</p>
 */
class ConfigurationImportServiceColumnOrderIntegrationTest {
    private static final String STANDARD_PAYLOAD =
            "metricCode|metricName|applicablePerspective|unit|formulaExpression|displayOrder|enabled|aiSummaryTemplate\n"
                    + "MARGIN_RATE|毛利率|ALL|%|REVENUE/SALES|20|true|毛利率说明\n";
    private static final String REORDERED_PAYLOAD =
            "formulaExpression|enabled|metricName|aiSummaryTemplate|displayOrder|metricCode|unit|applicablePerspective\n"
                    + "REVENUE/SALES|true|毛利率|毛利率说明|20|MARGIN_RATE|%|ALL\n";

    @Test
    void validatesStandardAndReorderedMetricPayloadsThroughApplicationChain() {
        FakeTaskRepository tasks = new FakeTaskRepository();
        FakeFileRepository files = new FakeFileRepository();
        FakeIssueRepository issues = new FakeIssueRepository();
        FakeMetricRepository metrics = new FakeMetricRepository();
        HeaderBasedValidator validator = new HeaderBasedValidator();
        HeaderBasedApplier applier = new HeaderBasedApplier(metrics);
        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        setField(service, "validator", validator);
        setField(service, "issueRepository", issues);
        setField(service, "applier", applier);

        ConfigurationImportTask standardTask = service.create(
                ConfigurationImportType.METRIC_DEFINITION, "metric-standard.xlsx", "checksum-1", "U001");
        ConfigurationImportTask reorderedTask = service.create(
                ConfigurationImportType.METRIC_DEFINITION, "metric-reordered.xlsx", "checksum-2", "U001");

        byte[] standard = STANDARD_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        byte[] reordered = REORDERED_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        ImportValidationResult standardResult = service.validate(
                standardTask.getTaskNo(), new ByteArrayInputStream(standard));
        ImportValidationResult reorderedResult = service.validate(
                reorderedTask.getTaskNo(), new ByteArrayInputStream(reordered));

        assertEquals(ImportStatus.VALID, standardResult.getStatus());
        assertEquals(ImportStatus.VALID, reorderedResult.getStatus());
        assertEquals(1, standardResult.getRowCount());
        assertEquals(1, reorderedResult.getRowCount());
        assertEquals(Arrays.asList("metricCode", "metricName", "applicablePerspective", "unit",
                "formulaExpression", "displayOrder", "enabled", "aiSummaryTemplate"),
                standardResult.getHeaders());
        assertEquals(Arrays.asList("formulaExpression", "enabled", "metricName", "aiSummaryTemplate",
                "displayOrder", "metricCode", "unit", "applicablePerspective"),
                reorderedResult.getHeaders());

        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(standardTask.getTaskNo()).get().getStatus());
        assertEquals(ImportStatus.VALID, tasks.findByTaskNo(reorderedTask.getTaskNo()).get().getStatus());
        assertArrayEquals(standard, files.content.get(standardTask.getTaskNo()));
        assertArrayEquals(reordered, files.content.get(reorderedTask.getTaskNo()));
        assertEquals(2, files.saveCalls);

        assertEquals(2, validator.calls.size());
        assertEquals(2, applier.calls.size());
        assertEquals(2, issues.replaceCalls);
        assertTrue(issues.findByTaskNo(standardTask.getTaskNo()).isEmpty());
        assertTrue(issues.findByTaskNo(reorderedTask.getTaskNo()).isEmpty());

        assertEquals(1, applier.results.get(standardTask.getTaskNo()).getAppliedCount());
        assertEquals(1, applier.results.get(reorderedTask.getTaskNo()).getAppliedCount());
        MetricDefinition saved = metrics.findByCode("MARGIN_RATE").get();
        assertMetric(saved);
        assertEquals(1, metrics.saveCalls);
        assertEquals(1, metrics.updateCalls);
    }

    private void setField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field field = ConfigurationImportServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("无法注入测试端口: " + name, ex);
        }
    }

    private void assertMetric(MetricDefinition actual) {
        assertEquals("MARGIN_RATE", actual.getMetricCode());
        assertEquals("毛利率", actual.getMetricName());
        assertEquals("ALL", actual.getApplicablePerspective());
        assertEquals("%", actual.getUnit());
        assertEquals("REVENUE/SALES", actual.getFormulaExpression());
        assertEquals(20, actual.getDisplayOrder());
        assertTrue(actual.isEnabled());
        assertEquals("毛利率说明", actual.getAiSummaryTemplate());
        assertEquals("U001", actual.getUpdatedBy());
    }

    private static final class ParsedPayload {
        private final List<String> headers;
        private final Map<String, String> values;

        private ParsedPayload(List<String> headers, Map<String, String> values) {
            this.headers = headers;
            this.values = values;
        }

        private static ParsedPayload read(InputStream inputStream) {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                String headerLine = reader.readLine();
                String valueLine = reader.readLine();
                if (headerLine == null || valueLine == null) {
                    throw new IllegalArgumentException("测试配置文件缺少标题或数据");
                }
                String[] headers = headerLine.split("\\|", -1);
                String[] values = valueLine.split("\\|", -1);
                if (headers.length != values.length) {
                    throw new IllegalArgumentException("测试配置文件标题与数据列数不一致");
                }
                Map<String, String> mapped = new HashMap<String, String>();
                for (int i = 0; i < headers.length; i++) mapped.put(headers[i], values[i]);
                return new ParsedPayload(Arrays.asList(headers), mapped);
            } catch (IOException ex) {
                throw new IllegalArgumentException("读取测试配置文件失败", ex);
            }
        }

        private String value(String header) {
            return values.get(header);
        }
    }

    private static final class HeaderBasedValidator implements ConfigurationTemplateValidator {
        private final List<String> calls = new ArrayList<String>();

        @Override
        public ImportValidationResult validate(String taskNo, ConfigurationImportType type,
                                               InputStream inputStream) {
            ParsedPayload payload = ParsedPayload.read(inputStream);
            calls.add(taskNo);
            return new ImportValidationResult(taskNo, ImportStatus.VALID, 1,
                    payload.headers, Collections.<ImportIssue>emptyList());
        }
    }

    private static final class HeaderBasedApplier implements ConfigurationImportApplier {
        private final FakeMetricRepository metrics;
        private final List<String> calls = new ArrayList<String>();
        private final Map<String, ConfigurationApplyResult> results =
                new HashMap<String, ConfigurationApplyResult>();

        private HeaderBasedApplier(FakeMetricRepository metrics) {
            this.metrics = metrics;
        }

        @Override
        public ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream inputStream) {
            ParsedPayload payload = ParsedPayload.read(inputStream);
            calls.add(task.getTaskNo());
            MetricDefinition current = metrics.findByCode(payload.value("metricCode")).orElse(null);
            MetricDefinition definition = current == null
                    ? new MetricDefinition(null, payload.value("metricCode"), payload.value("metricName"),
                    payload.value("applicablePerspective"), payload.value("unit"),
                    payload.value("formulaExpression"), Integer.parseInt(payload.value("displayOrder")),
                    Boolean.parseBoolean(payload.value("enabled")), payload.value("aiSummaryTemplate"),
                    task.getCreatedBy(), LocalDateTime.now())
                    : current;
            if (current != null) {
                definition.update(payload.value("metricName"), payload.value("applicablePerspective"),
                        payload.value("unit"), payload.value("formulaExpression"),
                        Integer.parseInt(payload.value("displayOrder")),
                        Boolean.parseBoolean(payload.value("enabled")), payload.value("aiSummaryTemplate"),
                        task.getCreatedBy());
            }
            metrics.save(definition);
            ConfigurationApplyResult result = new ConfigurationApplyResult(1,
                    Collections.<ImportIssue>emptyList());
            results.put(task.getTaskNo(), result);
            return result;
        }
    }

    private static final class FakeTaskRepository implements ConfigurationImportTaskRepository {
        private final Map<String, ConfigurationImportTask> data =
                new HashMap<String, ConfigurationImportTask>();

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

    private static final class FakeFileRepository implements ImportFileRepository {
        private final Map<String, byte[]> content = new HashMap<String, byte[]>();
        private int saveCalls;

        @Override
        public void save(String taskNo, String fileName, byte[] content) {
            this.content.put(taskNo, content);
            saveCalls++;
        }

        @Override
        public InputStream open(String taskNo) {
            return new ByteArrayInputStream(content.get(taskNo));
        }
    }

    private static final class FakeIssueRepository implements ImportIssueRepository {
        private final Map<String, List<ImportIssue>> data =
                new HashMap<String, List<ImportIssue>>();
        private int replaceCalls;

        @Override
        public void replace(String taskNo, List<ImportIssue> issues) {
            data.put(taskNo, new ArrayList<ImportIssue>(issues));
            replaceCalls++;
        }

        @Override
        public List<ImportIssue> findByTaskNo(String taskNo) {
            return data.containsKey(taskNo) ? data.get(taskNo) : Collections.<ImportIssue>emptyList();
        }
    }

    private static final class FakeMetricRepository implements MetricDefinitionRepository {
        private final Map<String, MetricDefinition> data = new HashMap<String, MetricDefinition>();
        private int saveCalls;
        private int updateCalls;

        @Override
        public MetricDefinition save(MetricDefinition definition) {
            if (definition.getId() == null) {
                definition = new MetricDefinition(1L, definition.getMetricCode(), definition.getMetricName(),
                        definition.getApplicablePerspective(), definition.getUnit(),
                        definition.getFormulaExpression(), definition.getDisplayOrder(), definition.isEnabled(),
                        definition.getAiSummaryTemplate(), definition.getUpdatedBy(), definition.getUpdatedAt());
                saveCalls++;
            } else {
                updateCalls++;
            }
            data.put(definition.getMetricCode(), definition);
            return definition;
        }

        @Override
        public Optional<MetricDefinition> findByCode(String metricCode) {
            return Optional.ofNullable(data.get(metricCode));
        }

        @Override
        public List<MetricDefinition> list(String perspective, boolean includeDisabled) {
            return new ArrayList<MetricDefinition>(data.values());
        }
    }
}
