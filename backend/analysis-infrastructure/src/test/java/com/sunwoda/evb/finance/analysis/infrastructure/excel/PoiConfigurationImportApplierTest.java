package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
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

        ConfigurationApplyResult first = applier.apply(task,
                workbook("匹配键", "映射值", "是否启用", "项目A", "产品线一", "是"));
        assertEquals(1, first.getAppliedCount());
        assertTrue(first.getIssues().isEmpty());
        assertEquals("产品线一", mappings.find(MappingType.SALES_PROJECT, "项目A").get().getMappedValue());
        ConfigurationApplyResult second = applier.apply(task,
                workbook("匹配键", "映射值", "是否启用", "项目A", "产品线二", "否"));
        assertEquals(1, second.getAppliedCount());
        assertTrue(second.getIssues().isEmpty());
        assertEquals("产品线二", mappings.find(MappingType.SALES_PROJECT, "项目A").get().getMappedValue());
        assertTrue(!mappings.find(MappingType.SALES_PROJECT, "项目A").get().isEnabled());
    }

    @Test
    void appliesRealMetricTemplateFormulaColumn() throws Exception {
        MetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(
                metrics, new InMemoryMappingEntryRepository());

        ConfigurationApplyResult result = applier.apply(task(ConfigurationImportType.METRIC_DEFINITION),
                realWorkbook("指标", new String[]{"指标编码", "指标名称", "计算公式", "适用视角", "单位",
                                "展示顺序", "是否有效", "AI简析模板"},
                        new String[]{"REV_ACT", "营业收入", "实际收入=收入成本明细营业收入合计", "通用",
                                "亿元", "10", "是", "请结合实际、预算、GAP及趋势生成简洁分析"}));

        assertEquals(1, result.getAppliedCount());
        assertEquals("实际收入=收入成本明细营业收入合计",
                metrics.findByCode("REV_ACT").get().getFormulaExpression());
        assertEquals(10, metrics.findByCode("REV_ACT").get().getDisplayOrder());
        assertTrue(metrics.findByCode("REV_ACT").get().isEnabled());
    }

    @Test
    void appliesRealBusinessFieldMappingTemplatesWithoutSyntheticColumns() throws Exception {
        MetricDefinitionRepository metrics = new InMemoryMetricDefinitionRepository();
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(metrics, mappings);

        assertRealMapping(applier, mappings, ConfigurationImportType.SALES_PROJECT_MAPPING,
                MappingType.SALES_PROJECT,
                new String[]{"客户简称", "客户项目", "电芯型号", "产品线", "事业部", "B品标识", "是否有效", "备注", "巴长", "巴长工号"},
                new String[]{"深海", "A9E模组", "106Ah", "产品线A", "事业部A", "否", "是", "", "张三", "E000001"},
                "深海|A9E模组|106Ah", "产品线A|事业部A|否||张三|E000001");
        assertRealMapping(applier, mappings, ConfigurationImportType.INVENTORY_MAPPING,
                MappingType.INVENTORY,
                new String[]{"物料编码", "电芯型号", "产品类型", "产品线", "事业部", "分摊比例", "是否有效", "备注"},
                new String[]{"MAT-001", "106Ah", "正常产品", "产品线A", "事业部A", "100%", "是", ""},
                "MAT-001", "106Ah|正常产品|产品线A|事业部A|100%|");
        assertRealMapping(applier, mappings, ConfigurationImportType.EXPENSE_CATEGORY,
                MappingType.EXPENSE_RULE,
                new String[]{"三级费用编码", "三级费用名称", "费用分类", "基地", "产品线", "事业部", "是否有效", "备注"},
                new String[]{"EXP-001", "人工费用", "基地运营", "义乌", "产品线A", "事业部A", "是", ""},
                "EXP-001", "基地运营|义乌|产品线A|事业部A|");
        assertRealMapping(applier, mappings, ConfigurationImportType.EXPENSE_ALLOCATION,
                MappingType.EXPENSE_RULE,
                new String[]{"期间", "费用项目", "来源基地", "目标基地", "分摊比例", "是否有效", "备注"},
                new String[]{"2026-08", "总部管理费用", "总部", "义乌", "60%", "是", ""},
                "2026-08|总部管理费用|总部|义乌", "60%|");
        assertRealMapping(applier, mappings, ConfigurationImportType.FX_RATE,
                MappingType.FX_RATE,
                new String[]{"期间", "基地", "币种", "汇率", "换算方向", "是否有效", "备注"},
                new String[]{"2026-08", "匈牙利", "EUR", "7.85", "原币×汇率=本位币", "是", ""},
                "2026-08|匈牙利|EUR", "7.85|原币×汇率=本位币|");
        assertRealMapping(applier, mappings, ConfigurationImportType.BASE_GRAIN,
                MappingType.BASE_GRAIN,
                new String[]{"基地", "电芯型号", "分析粒度", "是否有效", "备注"},
                new String[]{"义乌", "106Ah", "BASE_MODEL_PROJECT", "是", ""},
                "义乌|106Ah", "BASE_MODEL_PROJECT|");
    }

    @Test
    void retainsUserPermissionTemplateWithoutApplyingIt() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = new PoiConfigurationImportApplier(
                new InMemoryMetricDefinitionRepository(), mappings);

        ConfigurationApplyResult result = applier.apply(task(ConfigurationImportType.USER_PERMISSION),
                realWorkbook("数据录入", new String[]{"姓名", "工号", "用户角色", "数据视角", "可查看范围",
                                "明细查看权限", "数据导出权限", "配置维护范围", "是否有效", "备注"},
                        new String[]{"张三", "E000001", "基地财经", "基地", "义乌", "是", "是", "查看", "是", ""}));

        assertEquals(0, result.getAppliedCount());
        assertTrue(result.getIssues().isEmpty());
        assertTrue(mappings.list(MappingType.SALES_PROJECT, true).isEmpty());
    }

    private void assertRealMapping(PoiConfigurationImportApplier applier,
                                   MappingEntryRepository mappings,
                                   ConfigurationImportType importType,
                                   MappingType mappingType,
                                   String[] headers,
                                   String[] values,
                                   String expectedKey,
                                   String expectedValue) throws Exception {
        assertEquals(1, applier.apply(task(importType), realWorkbook("数据录入", headers, values)).getAppliedCount());
        assertEquals(expectedValue, mappings.find(mappingType, expectedKey).get().getMappedValue());
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

    private ByteArrayInputStream realWorkbook(String sheetName, String[] headers, String[] values) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }
}
