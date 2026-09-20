package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoiConfigurationTemplateValidatorTest {
    @Test
    void validatesConfigurationHeaderAndRowsWithoutApplyingBusinessRules() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("映射");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("匹配键");
        header.createCell(1).setCellValue("映射值");
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("项目A");
        row.createCell(1).setCellValue("产品线一");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-1", ConfigurationImportType.SALES_PROJECT_MAPPING,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getHeaders().size());
    }
}
