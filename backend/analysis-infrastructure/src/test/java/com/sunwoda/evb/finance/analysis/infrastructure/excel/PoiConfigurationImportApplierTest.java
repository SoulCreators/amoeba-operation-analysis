package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMetricDefinitionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiConfigurationImportApplierTest {
    @Test
    void appliesMetricDefinitionAndUpdatesSameCodeInsteadOfDuplicating() throws Exception {
        MetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(metrics, mappings);
        ConfigurationImportTask task = task(ConfigurationImportType.METRIC_DEFINITION);

        assertEquals(1, applier.apply(task, workbook("指标编码", "指标名称", "公式", "适用视角", "单位",
                "排序", "是否启用", "AI简析模板", "CUSTOM_METRIC", "自定义指标", "SUM(X)", "基地", "CNY", "90", "是", "模板")).getAppliedCount());
        assertEquals("自定义指标", metrics.findByCode("CUSTOM_METRIC").get().getMetricName());

        assertEquals(1, applier.apply(task, workbook("指标编码", "指标名称", "公式", "适用视角", "单位",
                "排序", "是否启用", "AI简析模板", "CUSTOM_METRIC", "更新后的指标", "SUM(Y)", "基地", "CNY", "90", "否", "新模板")).getAppliedCount());
        assertEquals("更新后的指标", metrics.findByCode("CUSTOM_METRIC").get().getMetricName());
        assertEquals(1, metrics.list("基地", true).stream().filter(item -> "CUSTOM_METRIC".equals(item.getMetricCode())).count());
    }

    @Test
    void appliesMappingAndUpdatesSameKey() throws Exception {
        MetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(metrics, mappings);
        ConfigurationImportTask task = task(ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertEquals(1, applier.apply(task, workbook("匹配键", "映射值", "是否启用", "项目A", "产品线一", "是")).getAppliedCount());
        assertEquals("产品线一", mappings.find(MappingType.SALES_PROJECT, "项目A").get().getMappedValue());
        assertEquals(1, applier.apply(task, workbook("匹配键", "映射值", "是否启用", "项目A", "产品线二", "否")).getAppliedCount());
        assertEquals("产品线二", mappings.find(MappingType.SALES_PROJECT, "项目A").get().getMappedValue());
        assertTrue(!mappings.find(MappingType.SALES_PROJECT, "项目A").get().isEnabled());
    }

    private ConfigurationImportTask task(ConfigurationImportType type) {
        return new ConfigurationImportTask(null, "CFG-TEST", type, "config.xlsx", null,
                "tester", LocalDateTime.now());
    }

    private ByteArrayInputStream workbook(String header1, String header2, String header3,
                                          String value1, String value2, String value3) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("配置");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue(header1);
        header.createCell(1).setCellValue(header2);
        header.createCell(2).setCellValue(header3);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue(value1);
        row.createCell(1).setCellValue(value2);
        row.createCell(2).setCellValue(value3);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }

    private ByteArrayInputStream workbook(String... values) throws Exception {
        if (values.length != 16) throw new IllegalArgumentException("metric workbook requires 16 values");
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("指标");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < 8; i++) header.createCell(i).setCellValue(values[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < 8; i++) row.createCell(i).setCellValue(values[i + 8]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }
}
