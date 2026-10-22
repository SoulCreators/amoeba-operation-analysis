package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void blocksSalesTemplateWithoutShippingBaseAndAdjustedProjectKey() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("21_销售项目映射");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"客户简称", "客户项目", "电芯型号", "产品线", "事业部", "是否有效"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        sheet.createRow(1).createCell(0).setCellValue("客户A");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-SALES-MISSING-KEY", ConfigurationImportType.SALES_PROJECT_MAPPING,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "出货基地".equals(issue.getFieldName())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "客户项目改".equals(issue.getFieldName())));
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

    @Test
    void recognizesFinanceDepartmentMasterTemplate() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("说明");
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("23_费用分类映射");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"公司段", "BU段", "部门段", "部门名称", "基地", "基地重分类", "部门性质", "备注", "是否有效"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) row.createCell(i).setCellValue(i == 0 ? "131" : "义乌");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-FINANCE-EXPENSE", ConfigurationImportType.EXPENSE_CATEGORY,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.VALID, result.getStatus());
        assertEquals(1, result.getRowCount());
    }

    @Test
    void requiresConfigurationMaintenanceScopeForPermissionTemplate() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("28_用户权限");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        String[] headers = {"姓名", "工号", "用户角色", "数据视角", "可查看范围",
                "明细查看权限", "数据导出权限", "是否有效"};
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        sheet.createRow(1).createCell(0).setCellValue("张三");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportValidationResult result = new PoiConfigurationTemplateValidator().validate(
                "CFG-PERMISSION", ConfigurationImportType.USER_PERMISSION,
                new ByteArrayInputStream(output.toByteArray()));

        assertEquals(ImportStatus.INVALID, result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "配置维护范围".equals(issue.getFieldName())));
    }
}
