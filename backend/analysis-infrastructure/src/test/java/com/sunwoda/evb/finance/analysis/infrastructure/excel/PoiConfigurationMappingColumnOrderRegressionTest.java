package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMappingEntryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.memory.InMemoryMetricDefinitionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UAT-002 technical regression coverage for business mapping templates.
 *
 * <p>Each workbook deliberately places the same fields in a different order.
 * The assertions inspect the persisted in-memory mapping entry, including its
 * business key, mapped value, type, and enabled state, rather than only the
 * apply result status.</p>
 */
class PoiConfigurationMappingColumnOrderRegressionTest {
    @Test
    void appliesReorderedSalesProjectMappingByHeaderName() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = applier(mappings);

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.SALES_PROJECT_MAPPING), workbook(
                        "21_销售项目映射",
                        new String[]{"是否有效", "备注", "事业部", "出货基地", "客户项目改", "电芯型号", "产品线",
                                "客户简称", "B品标识"},
                        new String[]{"否", "销售备注", "事业部A", "义乌", "项目A", "106Ah", "产品线A",
                                "客户A", "否"}));

        assertMapping(result, mappings, MappingType.SALES_PROJECT,
                "义乌|客户A|项目A|106Ah",
                "产品线A|事业部A|否|销售备注||", false);
    }

    @Test
    void appliesReorderedInventoryMappingByHeaderName() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = applier(mappings);

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.INVENTORY_MAPPING), workbook(
                        "22_库存档案映射",
                        new String[]{"备注", "是否有效", "产品线", "物料编码", "分摊比例", "电芯型号",
                                "事业部", "产品类型"},
                        new String[]{"库存备注", "是", "产品线I", "MAT-ORDER", "60%", "106Ah",
                                "事业部I", "正常产品"}));

        assertMapping(result, mappings, MappingType.INVENTORY,
                "MAT-ORDER",
                "106Ah|正常产品|产品线I|事业部I|60%|库存备注", true);
    }

    @Test
    void appliesReorderedExpenseCategoryMappingByHeaderName() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = applier(mappings);

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.EXPENSE_CATEGORY), workbook(
                        "23_费用分类映射",
                        new String[]{"备注", "是否有效", "基地", "三级费用名称", "事业部", "费用分类",
                                "三级费用编码", "产品线"},
                        new String[]{"分类备注", "否", "义乌", "人工费用", "事业部E", "基地运营",
                                "EXP-ORDER", "产品线E"}));

        assertMapping(result, mappings, MappingType.EXPENSE_RULE,
                "EXP-ORDER",
                "基地运营|义乌|产品线E|事业部E|分类备注", false);
    }

    @Test
    void appliesReorderedExpenseAllocationMappingByHeaderName() throws Exception {
        MappingEntryRepository mappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier applier = applier(mappings);

        ConfigurationApplyResult result = applier.apply(
                task(ConfigurationImportType.EXPENSE_ALLOCATION), workbook(
                        "数据录入",
                        new String[]{"备注", "目标基地", "是否有效", "分摊比例", "来源基地", "费用项目", "期间"},
                        new String[]{"分摊备注", "义乌", "是", "60%", "总部", "总部管理费用", "2026-08"}));

        assertMapping(result, mappings, MappingType.EXPENSE_RULE,
                "2026-08|总部管理费用|总部|义乌",
                "60%|分摊备注", true);
    }

    private PoiConfigurationImportApplier applier(MappingEntryRepository mappings) {
        return new PoiConfigurationImportApplier(
                new InMemoryMetricDefinitionRepository(), mappings);
    }

    private void assertMapping(ConfigurationApplyResult result,
                               MappingEntryRepository mappings,
                               MappingType type,
                               String expectedKey,
                               String expectedValue,
                               boolean expectedEnabled) {
        assertEquals(1, result.getAppliedCount());
        assertTrue(result.getIssues().isEmpty());

        MappingEntry entry = mappings.find(type, expectedKey)
                .orElseThrow(() -> new AssertionError("mapping was not applied: " + expectedKey));
        assertEquals(type, entry.getType());
        assertEquals(expectedKey, entry.getMatchKey());
        assertEquals(expectedValue, entry.getMappedValue());
        assertEquals(expectedEnabled, entry.isEnabled());
    }

    private ConfigurationImportTask task(ConfigurationImportType type) {
        return new ConfigurationImportTask(null, "CFG-MAPPING-COLUMN-ORDER", type,
                "config.xlsx", null, "tester", LocalDateTime.now());
    }

    private ByteArrayInputStream workbook(String sheetName, String[] headers, String[] values)
            throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(sheetName);
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            row.createCell(i).setCellValue(values[i]);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return new ByteArrayInputStream(output.toByteArray());
    }
}
