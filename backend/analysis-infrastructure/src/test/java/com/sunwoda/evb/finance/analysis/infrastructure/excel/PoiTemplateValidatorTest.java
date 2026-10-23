package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetFieldDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoiTemplateValidatorTest {
    @Test
    void reportsMissingRequiredHeaderAndEmptyValue() throws Exception {
        DatasetDefinition definition = definition();

        ImportValidationResult result = validate(definition, workbook(
                new String[]{"期间", "金额"}, new String[]{"2026-09", null}));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(2, result.getIssues().size());
        assertEquals(1, result.getRowCount());
        assertIssue(result.getIssues(), "FIELD_MISSING", 1, "收入计算基地", null);
        assertIssue(result.getIssues(), "FIELD_EMPTY", 2, "金额", "");
    }

    @Test
    void reportsMissingHeaderRowWithFileLocation() throws Exception {
        ImportValidationResult result = validate(definition(), workbook(null, null));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getRowCount());
        assertTrue(result.getHeaders().isEmpty());
        assertEquals(1, result.getIssues().size());
        assertIssue(result.getIssues(), "HEADER_MISSING", 1, null, null);
    }

    @Test
    void retainsUnknownHeaderButDoesNotClassifyItAsAnIssue() throws Exception {
        ImportValidationResult result = validate(definition(), workbook(
                new String[]{"期间", "金额", "收入计算基地", "未知列"},
                new String[]{"2026-09", "100.00", "基地A", "原始值"}));

        // This is the current validator behavior only. The requirement-level
        // warning/retention semantics for unknown columns are not implemented here.
        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertEquals(Arrays.asList("期间", "金额", "收入计算基地", "未知列"), result.getHeaders());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void classifiesEmptyInputAsFileInvalidWithoutRowLocation() throws Exception {
        ImportValidationResult result = validate(definition(), new byte[0]);

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getRowCount());
        assertTrue(result.getHeaders().isEmpty());
        assertEquals(1, result.getIssues().size());
        assertIssue(result.getIssues(), "FILE_INVALID", null, null, null);
    }

    @Test
    void classifiesCorruptedInputAsFileInvalidWithoutRowLocation() throws Exception {
        ImportValidationResult result = validate(definition(),
                "not-an-excel-file".getBytes(StandardCharsets.UTF_8));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(0, result.getRowCount());
        assertTrue(result.getHeaders().isEmpty());
        assertEquals(1, result.getIssues().size());
        assertIssue(result.getIssues(), "FILE_INVALID", null, null, null);
    }

    @Test
    void locatesDataSheetByNameInMultiSheetWorkbook() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet summary = workbook.createSheet("导出摘要");
            summary.createRow(0).createCell(0).setCellValue("与损益无关的摘要");
            org.apache.poi.ss.usermodel.Sheet data = workbook.createSheet("2.6收入成本明细-实际");
            String[] headers = {"期间", "客户项目", "客户简称-改2", "电芯型号", "销售收入", "销售成本"};
            org.apache.poi.ss.usermodel.Row header = data.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            org.apache.poi.ss.usermodel.Row row = data.createRow(1);
            row.createCell(0).setCellValue("2026-09");
            row.createCell(1).setCellValue("项目A");
            row.createCell(2).setCellValue("客户X");
            row.createCell(3).setCellValue("106Ah");
            row.createCell(4).setCellValue(1000);
            row.createCell(5).setCellValue(700);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);

            ImportValidationResult result = validate(plbuDefinition(), output.toByteArray());

            assertEquals(ImportStatus.VALID, result.getStatus());
            assertEquals(1, result.getRowCount());
            assertTrue(result.getIssues().isEmpty());
        } finally {
            workbook.close();
        }
    }

    private ImportValidationResult validate(DatasetDefinition definition, byte[] workbook) {
        return new PoiTemplateValidator().validate("IMP-ERR-001", definition,
                new ByteArrayInputStream(workbook));
    }

    private byte[] workbook(String[] headers, String[] values) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        try {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("数据");
            if (headers != null) {
                org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    header.createCell(i).setCellValue(headers[i]);
                }
            }
            if (values != null) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != null) row.createCell(i).setCellValue(values[i]);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.close();
        }
    }

    private DatasetDefinition definition() {
        return new DatasetDefinition("T", "测试", "MONTHLY", true,
                Arrays.asList(AnalysisPerspective.BASE), Arrays.asList(
                new DatasetFieldDefinition("period", "期间", "MONTH", true),
                new DatasetFieldDefinition("amount", "金额", "DECIMAL", true),
                new DatasetFieldDefinition("base", "收入计算基地", "TEXT", true)));
    }

    private DatasetDefinition plbuDefinition() {
        return new DatasetDefinition("PLBU_ACT_INC_COST", "产品线/事业部实际收入成本", "MONTHLY", true,
                Arrays.asList(AnalysisPerspective.PRODUCT_LINE, AnalysisPerspective.BUSINESS_UNIT), Arrays.asList(
                new DatasetFieldDefinition("期间", "期间", "MONTH", true),
                new DatasetFieldDefinition("项目", "客户项目", "TEXT", true),
                new DatasetFieldDefinition("客户", "客户简称-改2", "TEXT", true),
                new DatasetFieldDefinition("型号", "电芯型号", "TEXT", true),
                new DatasetFieldDefinition("收入", "销售收入", "DECIMAL", true),
                new DatasetFieldDefinition("成本", "销售成本", "DECIMAL", true)));
    }

    private void assertIssue(List<ImportIssue> issues, String code, Integer rowNo,
                             String fieldName, String rawValue) {
        ImportIssue issue = issues.stream()
                .filter(candidate -> code.equals(candidate.getIssueCode()))
                .findFirst()
                .orElse(null);
        assertNotNull(issue, "missing issue code: " + code);
        assertEquals(rowNo, issue.getRowNo());
        assertEquals(fieldName, issue.getFieldName());
        assertEquals(rawValue, issue.getRawValue());
    }
}
