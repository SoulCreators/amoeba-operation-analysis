package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
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
 * UAT-002 regression coverage for BASE_GRAIN and USER_PERMISSION.
 *
 * BASE_GRAIN has a stable mapping key/value contract, so the test compares the
 * actual persisted mapping after reordering the columns. USER_PERMISSION is
 * intentionally validation-only in the current adapter; its assertions stop
 * at the structural validation and non-writing application boundary, without
 * inferring any permission business rule.
 */
class PoiConfigurationBaseAndPermissionColumnOrderRegressionTest {
    @Test
    void baseGrainColumnOrderDoesNotChangeValidationOrAppliedMapping() throws Exception {
        byte[] canonical = workbook("数据录入",
                new String[]{"基地", "电芯型号", "分析粒度", "是否有效", "备注"},
                new String[]{"义乌", "106Ah", "BASE_MODEL_PROJECT", "是", ""});
        byte[] reordered = workbook("数据录入",
                new String[]{"备注", "是否有效", "分析粒度", "电芯型号", "基地"},
                new String[]{"", "是", "BASE_MODEL_PROJECT", "106Ah", "义乌"});

        PoiConfigurationTemplateValidator validator = new PoiConfigurationTemplateValidator();
        assertValidBaseGrain(validator, canonical, "BASE-CANONICAL");
        assertValidBaseGrain(validator, reordered, "BASE-REORDERED");

        MappingEntryRepository canonicalMappings = new InMemoryMappingEntryRepository();
        MappingEntryRepository reorderedMappings = new InMemoryMappingEntryRepository();
        PoiConfigurationImportApplier canonicalApplier = applier(canonicalMappings);
        PoiConfigurationImportApplier reorderedApplier = applier(reorderedMappings);

        ConfigurationApplyResult canonicalResult = canonicalApplier.apply(
                task(ConfigurationImportType.BASE_GRAIN), new ByteArrayInputStream(canonical));
        ConfigurationApplyResult reorderedResult = reorderedApplier.apply(
                task(ConfigurationImportType.BASE_GRAIN), new ByteArrayInputStream(reordered));

        assertEquals(1, canonicalResult.getAppliedCount());
        assertTrue(canonicalResult.getIssues().isEmpty());
        assertEquals(1, reorderedResult.getAppliedCount());
        assertTrue(reorderedResult.getIssues().isEmpty());
        assertEquals("BASE_MODEL_PROJECT|",
                canonicalMappings.find(MappingType.BASE_GRAIN, "义乌|106Ah").get().getMappedValue());
        assertEquals("BASE_MODEL_PROJECT|",
                reorderedMappings.find(MappingType.BASE_GRAIN, "义乌|106Ah").get().getMappedValue());
        assertEquals(canonicalMappings.list(MappingType.BASE_GRAIN, true).size(),
                reorderedMappings.list(MappingType.BASE_GRAIN, true).size());
    }

    @Test
    void userPermissionColumnOrderDoesNotChangeValidationOnlyApplicationBoundary() throws Exception {
        byte[] canonical = workbook("28_用户权限",
                new String[]{"姓名", "工号", "用户角色", "数据视角", "可查看范围",
                        "明细查看权限", "数据导出权限", "配置维护范围", "是否有效", "备注"},
                new String[]{"张三", "E000001", "基地财经", "基地", "义乌",
                        "是", "是", "查看", "是", ""});
        byte[] reordered = workbook("28_用户权限",
                new String[]{"备注", "是否有效", "配置维护范围", "数据导出权限", "明细查看权限",
                        "可查看范围", "数据视角", "用户角色", "工号", "姓名"},
                new String[]{"", "是", "查看", "是", "是", "义乌", "基地", "基地财经",
                        "E000001", "张三"});

        PoiConfigurationTemplateValidator validator = new PoiConfigurationTemplateValidator();
        assertValidPermission(validator, canonical, "PERMISSION-CANONICAL");
        assertValidPermission(validator, reordered, "PERMISSION-REORDERED");

        MappingEntryRepository canonicalMappings = new InMemoryMappingEntryRepository();
        MappingEntryRepository reorderedMappings = new InMemoryMappingEntryRepository();
        ConfigurationApplyResult canonicalResult = applier(canonicalMappings).apply(
                task(ConfigurationImportType.USER_PERMISSION), new ByteArrayInputStream(canonical));
        ConfigurationApplyResult reorderedResult = applier(reorderedMappings).apply(
                task(ConfigurationImportType.USER_PERMISSION), new ByteArrayInputStream(reordered));

        // Current contract: permission rows are checked but deliberately not persisted.
        assertEquals(0, canonicalResult.getAppliedCount());
        assertTrue(canonicalResult.getIssues().isEmpty());
        assertEquals(0, reorderedResult.getAppliedCount());
        assertTrue(reorderedResult.getIssues().isEmpty());
        assertTrue(canonicalMappings.list(MappingType.BASE_GRAIN, true).isEmpty());
        assertTrue(reorderedMappings.list(MappingType.BASE_GRAIN, true).isEmpty());
    }

    private void assertValidBaseGrain(PoiConfigurationTemplateValidator validator,
                                      byte[] workbook, String taskNo) {
        assertEquals(ImportStatus.VALID, validator.validate(taskNo, ConfigurationImportType.BASE_GRAIN,
                new ByteArrayInputStream(workbook)).getStatus());
        assertEquals(1, validator.validate(taskNo, ConfigurationImportType.BASE_GRAIN,
                new ByteArrayInputStream(workbook)).getRowCount());
    }

    private void assertValidPermission(PoiConfigurationTemplateValidator validator,
                                       byte[] workbook, String taskNo) {
        assertEquals(ImportStatus.VALID, validator.validate(taskNo, ConfigurationImportType.USER_PERMISSION,
                new ByteArrayInputStream(workbook)).getStatus());
        assertEquals(1, validator.validate(taskNo, ConfigurationImportType.USER_PERMISSION,
                new ByteArrayInputStream(workbook)).getRowCount());
    }

    private PoiConfigurationImportApplier applier(MappingEntryRepository mappings) {
        return new PoiConfigurationImportApplier(new InMemoryMetricDefinitionRepository(), mappings);
    }

    private ConfigurationImportTask task(ConfigurationImportType type) {
        return new ConfigurationImportTask(null, "CFG-COLUMN-ORDER", type, "config.xlsx", null,
                "tester", LocalDateTime.now());
    }

    private byte[] workbook(String sheetName, String[] headers, String[] values) throws Exception {
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
        return output.toByteArray();
    }
}
