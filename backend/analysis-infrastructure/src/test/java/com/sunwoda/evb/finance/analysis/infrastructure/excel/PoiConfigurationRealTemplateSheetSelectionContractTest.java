package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Technical contract tests for the sheet layout used by the real import templates.
 *
 * <p>The current adapter recognizes type-specific sheet names and otherwise falls
 * back to the first sheet. These tests record that behavior only; they do not
 * decide whether a missing business sheet should eventually block the import.</p>
 */
class PoiConfigurationRealTemplateSheetSelectionContractTest {
    private final PoiConfigurationTemplateValidator validator = new PoiConfigurationTemplateValidator();

    @Test
    void realTemplateWithDataEntryFirstUsesFirstSheetWhenExpectedSheetIsAbsent() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        addSalesSheet(workbook, "数据录入");
        workbook.createSheet("字段说明");
        workbook.createSheet("示例数据");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        // Current behavior: 21_销售项目映射 is absent, so the first sheet is used.
        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertEquals(Arrays.asList("出货基地", "客户简称", "客户项目改", "电芯型号", "产品线", "事业部", "是否有效"),
                result.getHeaders());
    }

    @Test
    void explanationSheetBeforeDataEntryIsValidatedByCurrentFallback() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet explanation = workbook.createSheet("字段说明");
        org.apache.poi.ss.usermodel.Row explanationHeader = explanation.createRow(0);
        explanationHeader.createCell(0).setCellValue("字段名");
        explanationHeader.createCell(1).setCellValue("说明");
        addSalesSheet(workbook, "数据录入");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        // Current behavior: the adapter falls back to the first (explanation) sheet.
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(Arrays.asList("字段名", "说明"), result.getHeaders());
        assertTrue(hasIssue(result, "FIELD_MISSING"));
        assertTrue(hasIssue(result, "DATA_MISSING"));
    }

    @Test
    void expectedBusinessSheetIsSelectedWhenItIsNotFirst() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("字段说明");
        addSalesSheet(workbook, "21_销售项目映射");
        workbook.createSheet("示例数据");
        addSalesSheet(workbook, "数据录入");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        // Current behavior: the named business sheet wins over its position.
        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertEquals("出货基地", result.getHeaders().get(0));
    }

    @Test
    void missingExpectedBusinessSheetSilentlyFallsBackToFirstRealTemplateSheet() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        addSalesSheet(workbook, "数据录入");
        workbook.createSheet("字段说明");
        workbook.createSheet("示例数据");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        // The missing 21_销售项目映射 sheet does not itself produce an issue today.
        assertEquals(ImportStatus.VALID, result.getStatus());
        assertTrue(result.getIssues().isEmpty());
        assertEquals(1, result.getRowCount());
    }

    private void addSalesSheet(XSSFWorkbook workbook, String name) {
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(name);
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"出货基地", "客户简称", "客户项目改", "电芯型号", "产品线", "事业部", "是否有效"};
        String[] values = {"义乌", "客户A", "项目A", "106Ah", "动力", "一部", "是"};
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private ImportValidationResult validate(XSSFWorkbook workbook, ConfigurationImportType type) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return validator.validate("REAL-TEMPLATE-SHEET", type,
                new ByteArrayInputStream(output.toByteArray()));
    }

    private boolean hasIssue(ImportValidationResult result, String issueCode) {
        return result.getIssues().stream()
                .map(ImportIssue::getIssueCode)
                .anyMatch(issueCode::equals);
    }
}
