package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshotImportPayload;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PoiFcSnapshotTemplateParserTest {
    @Test
    void parsesValidSnapshotRowsAndNormalizesMetricCode() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("FC快照");
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("期间");
        header.createCell(1).setCellValue("范围编码");
        header.createCell(2).setCellValue("指标编码");
        header.createCell(3).setCellValue("预测值");
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("2026-10");
        row.createCell(1).setCellValue("BU-A");
        row.createCell(2).setCellValue("revenue");
        row.createCell(3).setCellValue("1,234.50");
        workbook.write(output);
        workbook.close();

        FcSnapshotImportPayload payload = new PoiFcSnapshotTemplateParser().parse(
                new ByteArrayInputStream(output.toByteArray()), "FC-1");

        assertEquals(1, payload.getRowCount());
        assertEquals(1, payload.getSnapshots().size());
        assertEquals("REVENUE", payload.getSnapshots().get(0).getMetricCode());
        assertEquals("1234.50", payload.getSnapshots().get(0).getValue().toPlainString());
        assertEquals(0, payload.getIssues().size());
    }

    @Test
    void rejectsInvalidMetricAndDuplicateKey() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("FC快照");
        String[] headers = {"期间", "范围", "指标", "值"};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        for (int i = 1; i <= 2; i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(i);
            row.createCell(0).setCellValue("2026-10");
            row.createCell(1).setCellValue("BU-A");
            row.createCell(2).setCellValue(i == 1 ? "UNKNOWN" : "REVENUE");
            row.createCell(3).setCellValue("10");
        }
        workbook.write(output);
        workbook.close();

        FcSnapshotImportPayload payload = new PoiFcSnapshotTemplateParser().parse(
                new ByteArrayInputStream(output.toByteArray()), "FC-2");

        assertFalse(payload.getIssues().isEmpty());
        assertEquals(2, payload.getRowCount());
    }
}
