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

    @Test
    void recognizesEnglishAliasesForLegacyGenericMapping() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("映射");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("matchKey");
        header.createCell(1).setCellValue("mappedValue");
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("项目A");
        row.createCell(1).setCellValue("产品线一");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-LEGACY", ConfigurationImportType.SALES_PROJECT_MAPPING,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.VALID, result.getStatus());
    }

    @Test
    void recognizesRequiredFieldsInRealMetricTemplate() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("数据录入");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"指标编码", "指标名称", "计算公式", "适用视角", "单位", "展示顺序", "是否有效", "AI简析模板"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("REV_ACT");
        row.createCell(1).setCellValue("营业收入");
        row.createCell(2).setCellValue("实际收入=收入成本明细营业收入合计");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-2", ConfigurationImportType.METRIC_DEFINITION,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(8, result.getHeaders().size());
    }
}
