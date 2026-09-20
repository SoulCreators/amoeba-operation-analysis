package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetFieldDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoiTemplateValidatorTest {
    @Test
    void reportsMissingRequiredHeaderAndEmptyValue() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("数据");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("期间");
        header.createCell(1).setCellValue("金额");
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("2026-09");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        DatasetDefinition definition = new DatasetDefinition("T", "测试", "MONTHLY", true,
                Arrays.asList(AnalysisPerspective.BASE), Arrays.asList(
                new DatasetFieldDefinition("period", "期间", "MONTH", true),
                new DatasetFieldDefinition("amount", "金额", "DECIMAL", true),
                new DatasetFieldDefinition("base", "收入计算基地", "TEXT", true)));

        ImportValidationResult result = new PoiTemplateValidator().validate("IMP-1", definition,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertEquals(2, result.getIssues().size());
        assertEquals(1, result.getRowCount());
    }
}
