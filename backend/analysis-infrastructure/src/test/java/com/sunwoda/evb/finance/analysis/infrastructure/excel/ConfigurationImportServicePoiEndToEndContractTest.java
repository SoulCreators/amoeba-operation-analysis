package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryConfigurationImportTaskRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryImportFileRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryImportIssueRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMetricDefinitionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combines the real POI validator, application service and POI applier.
 * Database persistence remains a separate opt-in contract.
 */
class ConfigurationImportServicePoiEndToEndContractTest {
    @Test
    void realExcelBytesFlowThroughValidationServiceAndMetricApplication() throws Exception {
        InMemoryConfigurationImportTaskRepository tasks =
                new InMemoryConfigurationImportTaskRepository();
        InMemoryImportFileRepository files = new InMemoryImportFileRepository();
        InMemoryImportIssueRepository issues = new InMemoryImportIssueRepository();
        InMemoryMetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        wire(service, new PoiConfigurationTemplateValidator(), issues,
                new PoiConfigurationImportApplier(metrics, new InMemoryMappingEntryRepository()));

        ConfigurationImportTask task = service.create(ConfigurationImportType.METRIC_DEFINITION,
                "metric-reordered.xlsx", "checksum-poi-001", "U001");
        byte[] workbook = workbookBytes(
                new String[] {"是否有效", "公式", "指标名称", "指标编码", "展示顺序"},
                new String[] {"是", "SUM(REVENUE)", "营业收入回归", "REG-POI-001", "17"});

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbook));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertTrue(result.getIssues().isEmpty());
        assertEquals(ImportStatus.VALID, service.get(task.getTaskNo()).getStatus());
        assertTrue(issues.findByTaskNo(task.getTaskNo()).isEmpty());
        assertArrayEquals(workbook, read(files.open(task.getTaskNo())));

        MetricDefinition definition = metrics.findByCode("REG-POI-001").orElse(null);
        assertTrue(definition != null);
        assertEquals("营业收入回归", definition.getMetricName());
        assertEquals("SUM(REVENUE)", definition.getFormulaExpression());
        assertEquals(17, definition.getDisplayOrder());
        assertTrue(definition.isEnabled());
    }

    @Test
    void realExcelBytesFlowThroughValidationServiceAndSalesMappingApplication() throws Exception {
        InMemoryConfigurationImportTaskRepository tasks =
                new InMemoryConfigurationImportTaskRepository();
        InMemoryImportFileRepository files = new InMemoryImportFileRepository();
        InMemoryImportIssueRepository issues = new InMemoryImportIssueRepository();
        InMemoryMappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        ConfigurationImportServiceImpl service = new ConfigurationImportServiceImpl(tasks, files);
        wire(service, new PoiConfigurationTemplateValidator(), issues,
                new PoiConfigurationImportApplier(new InMemoryMetricDefinitionRepository(), mappings));

        ConfigurationImportTask task = service.create(ConfigurationImportType.SALES_PROJECT_MAPPING,
                "sales-project.xlsx", "checksum-poi-002", "U002");
        byte[] workbook = workbookBytes("21_销售项目映射",
                new String[] {"是否有效", "备注", "事业部", "出货基地", "客户项目改", "电芯型号", "产品线",
                        "客户简称", "B品标识"},
                new String[] {"否", "销售备注", "事业部A", "义乌", "项目A", "106Ah", "产品线A",
                        "客户A", "否"});

        ImportValidationResult result = service.validate(task.getTaskNo(),
                new ByteArrayInputStream(workbook));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertTrue(result.getIssues().isEmpty());
        MappingEntry entry = mappings.find(MappingType.SALES_PROJECT,
                "义乌|客户A|项目A|106Ah").orElse(null);
        assertTrue(entry != null);
        assertEquals("产品线A|事业部A|否|销售备注||", entry.getMappedValue());
        assertEquals(false, entry.isEnabled());
    }

    private static byte[] workbookBytes(String[] headers, String[] values) throws Exception {
        return workbookBytes("指标", headers, values);
    }

    private static byte[] workbookBytes(String sheetName, String[] headers, String[] values)
            throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                row.createCell(i).setCellValue(values[i]);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > 0) output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static void wire(ConfigurationImportServiceImpl service,
                             PoiConfigurationTemplateValidator validator,
                             InMemoryImportIssueRepository issues,
                             PoiConfigurationImportApplier applier) throws Exception {
        set(service, "validator", validator);
        set(service, "issueRepository", issues);
        set(service, "applier", applier);
    }

    private static void set(ConfigurationImportServiceImpl service, String fieldName,
                            Object value) throws Exception {
        Field field = ConfigurationImportServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
