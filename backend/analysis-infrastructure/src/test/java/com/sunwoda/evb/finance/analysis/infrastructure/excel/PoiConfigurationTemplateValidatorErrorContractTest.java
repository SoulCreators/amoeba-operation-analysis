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

class PoiConfigurationTemplateValidatorErrorContractTest {
    private final PoiConfigurationTemplateValidator validator = new PoiConfigurationTemplateValidator();

    @Test
    void reportsMissingSheetWhenWorkbookHasNoSheet() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertIssue(result, "SHEET_MISSING");
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getRowCount());
    }

    @Test
    void reportsMissingHeaderWhenFirstRowDoesNotExist() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("配置");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertIssue(result, "HEADER_MISSING");
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getHeaders().size());
    }

    @Test
    void reportsEmptyHeaderCells() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("配置");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("");
        header.createCell(1).setCellValue(" ");
        header.createCell(2).setCellValue("映射值");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertIssue(result, "HEADER_EMPTY");
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(Arrays.asList("", "", "映射值"), result.getHeaders());
    }

    @Test
    void reportsDuplicateHeadersAfterNormalization() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("配置");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("matchKey");
        header.createCell(1).setCellValue(" MATCH KEY ");
        header.createCell(2).setCellValue("mappedValue");
        sheet.createRow(1).createCell(0).setCellValue("项目A");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertIssue(result, "DUPLICATE_HEADER");
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(1, result.getRowCount());
    }

    @Test
    void reportsMissingDataWhenHeaderExistsButRowsAreBlank() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("配置");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("matchKey");
        header.createCell(1).setCellValue("mappedValue");
        sheet.createRow(1);
        org.apache.poi.ss.usermodel.Row blankRow = sheet.createRow(2);
        blankRow.createCell(0).setCellValue(" ");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.SALES_PROJECT_MAPPING);

        assertIssue(result, "DATA_MISSING");
        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getRowCount());
    }

    @Test
    void reportsInvalidForEmptyAndCorruptedFiles() {
        ImportValidationResult empty = validator.validate(
                "EMPTY", ConfigurationImportType.SALES_PROJECT_MAPPING,
                new ByteArrayInputStream(new byte[0]));
        ImportValidationResult corrupted = validator.validate(
                "CORRUPTED", ConfigurationImportType.SALES_PROJECT_MAPPING,
                new ByteArrayInputStream(new byte[]{0x01, 0x02, 0x03, 0x04}));

        assertIssue(empty, "FILE_INVALID");
        assertEquals(ImportStatus.INVALID, empty.getStatus());
        assertIssue(corrupted, "FILE_INVALID");
        assertEquals(ImportStatus.INVALID, corrupted.getStatus());
    }

    @Test
    void selectsExpectedBusinessSheetWhenItIsNotFirst() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("说明");
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("23_费用分类映射");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("三级费用名称");
        header.createCell(1).setCellValue("费用分类");
        header.createCell(2).setCellValue("是否有效");
        sheet.createRow(1).createCell(0).setCellValue("差旅费");

        ImportValidationResult result = validate(workbook, ConfigurationImportType.EXPENSE_CATEGORY);

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertEquals(Arrays.asList("三级费用名称", "费用分类", "是否有效"), result.getHeaders());
    }

    private ImportValidationResult validate(XSSFWorkbook workbook, ConfigurationImportType type) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return validator.validate("ERROR-CONTRACT", type,
                new ByteArrayInputStream(output.toByteArray()));
    }

    private void assertIssue(ImportValidationResult result, String issueCode) {
        assertTrue(result.getIssues().stream().map(ImportIssue::getIssueCode).anyMatch(issueCode::equals),
                "Expected issue code: " + issueCode + ", actual: " + result.getIssues());
    }
}
