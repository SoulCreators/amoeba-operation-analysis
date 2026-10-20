package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMetricDefinitionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-IMP-001/UAT-002 technical regression coverage.
 *
 * <p>The workbooks intentionally put the same business fields in a different
 * order. Assertions inspect the applied domain objects, not only validation
 * status, so a positional parser cannot satisfy this contract.</p>
 */
class PoiConfigurationImportColumnOrderRegressionTest {
    @Test
    void appliesReorderedMetricColumnsByHeaderName() throws Exception {
        InMemoryMetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(
                metrics, new InMemoryMappingEntryRepository());

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.METRIC_DEFINITION), workbook(
                        "指标", new String[]{"AI简析模板", "展示顺序", "指标名称", "是否有效",
                                "指标编码", "单位", "计算公式", "适用视角"},
                        new Object[]{"按收入和成本变化生成分析", 70, "列顺序回归指标", "否",
                                "COLUMN_ORDER_METRIC", "亿元", "实际收入-实际成本", "基地"}));

        assertEquals(1, result.getAppliedCount());
        assertTrue(result.getIssues().isEmpty());

        MetricDefinition definition = metrics.findByCode("COLUMN_ORDER_METRIC").orElse(null);
        assertTrue(definition != null);
        assertEquals("列顺序回归指标", definition.getMetricName());
        assertEquals("基地", definition.getApplicablePerspective());
        assertEquals("亿元", definition.getUnit());
        assertEquals("实际收入-实际成本", definition.getFormulaExpression());
        assertEquals(70, definition.getDisplayOrder());
        assertFalse(definition.isEnabled());
        assertEquals("按收入和成本变化生成分析", definition.getAiSummaryTemplate());
    }

    @Test
    void appliesReorderedMappingColumnsByHeaderNameAndFormatsNumericValue() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(
                new InMemoryMetricDefinitionRepository(), mappings);

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.FX_RATE), workbook(
                        "数据录入", new String[]{"是否有效", "换算方向", "汇率", "币种", "基地", "备注", "期间"},
                        new Object[]{"是", "原币×汇率=本位币", 7.85d, "EUR", "匈牙利",
                                "列顺序回归汇率", "2026-08"}));

        assertEquals(1, result.getAppliedCount());
        assertTrue(result.getIssues().isEmpty());
        assertEquals("7.85|原币×汇率=本位币|列顺序回归汇率",
                mappings.find(MappingType.FX_RATE, "2026-08|匈牙利|EUR")
                        .orElseThrow(() -> new AssertionError("FX mapping was not applied"))
                        .getMappedValue());
        assertTrue(mappings.find(MappingType.FX_RATE, "2026-08|匈牙利|EUR").get().isEnabled());
    }

    private ConfigurationImportTask task(ConfigurationImportType type) {
        return new ConfigurationImportTask(null, "CFG-COLUMN-ORDER", type, "config.xlsx", null,
                "tester", LocalDateTime.now());
    }

    private ByteArrayInputStream workbook(String sheetName, String[] headers, Object[] values)
            throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            if (values[i] instanceof Number) {
                row.createCell(i).setCellValue(((Number) values[i]).doubleValue());
            } else {
                row.createCell(i).setCellValue((String) values[i]);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }
}
