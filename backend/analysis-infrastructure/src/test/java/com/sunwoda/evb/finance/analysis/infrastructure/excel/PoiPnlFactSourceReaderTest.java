package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoiPnlFactSourceReaderTest {
    @Test
    void readsSheetNameMetadataAndKeepsRowsWithSameDimensionsSeparate() throws Exception {
        XSSFWorkbook workbook = workbook("实际明细");
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        headers(sheet, "收入计算基地", "产品线", "电芯型号", "拉线");
        row(sheet, 1, "南昌", "乘用车", "106Ah", "一线");
        row(sheet, 2, "南昌", "乘用车", "106Ah", "一线");

        List<PnlFactSource> sources = read(workbook, AnalysisPerspective.BASE);

        assertEquals(2, sources.size());
        assertEquals("实际明细", sources.get(0).getSheetName());
        assertEquals("actual.xlsx", sources.get(0).getFileName());
        assertEquals("sha-256", sources.get(0).getChecksum());
        assertEquals(2, sources.get(0).getRowNo());
        assertEquals(3, sources.get(1).getRowNo());
        assertEquals("南昌", sources.get(0).getScopeCode());
        assertEquals("南昌", sources.get(1).getScopeCode());
        assertEquals("乘用车", sources.get(0).getProductCode());
        assertEquals("一线", sources.get(0).getLineCode());
        workbook.close();
    }

    @Test
    void skipsBlankRowsAndRowsWithoutScope() throws Exception {
        XSSFWorkbook workbook = workbook("实际");
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        headers(sheet, "收入计算基地", "产品线", "电芯型号", "拉线");
        row(sheet, 1, "", "乘用车", "106Ah", "一线");
        row(sheet, 2, "   ", "乘用车", "106Ah", "一线");
        row(sheet, 3, "南昌", "乘用车", "106Ah", "一线");

        List<PnlFactSource> sources = read(workbook, AnalysisPerspective.BASE);

        assertEquals(1, sources.size());
        assertEquals(4, sources.get(0).getRowNo());
        workbook.close();
    }

    @Test
    void readsOnlyTheFirstSheet() throws Exception {
        XSSFWorkbook workbook = workbook("第一张");
        org.apache.poi.ss.usermodel.Sheet first = workbook.getSheetAt(0);
        headers(first, "收入计算基地", "产品线");
        row(first, 1, "南昌", "第一张项目");
        org.apache.poi.ss.usermodel.Sheet second = workbook.createSheet("第二张");
        headers(second, "收入计算基地", "产品线");
        row(second, 1, "上海", "第二张项目");

        List<PnlFactSource> sources = read(workbook, AnalysisPerspective.BASE);

        assertEquals(1, sources.size());
        assertEquals("第一张", sources.get(0).getSheetName());
        assertEquals("南昌", sources.get(0).getScopeCode());
        workbook.close();
    }

    @Test
    void mapsScopeCandidatesForEachPerspectiveAndDimensions() throws Exception {
        XSSFWorkbook workbook = workbook("维度映射");
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        headers(sheet, "收入计算基地", "产品线", "事业部", "电芯型号", "拉线");
        row(sheet, 1, "南昌", "乘用车", "新能源事业部", "106Ah", "一线");

        assertEquals("南昌", read(workbook, AnalysisPerspective.BASE).get(0).getScopeCode());
        assertEquals("乘用车", read(workbook, AnalysisPerspective.PRODUCT_LINE).get(0).getScopeCode());
        assertEquals("新能源事业部", read(workbook, AnalysisPerspective.BUSINESS_UNIT).get(0).getScopeCode());
        PnlFactSource source = read(workbook, AnalysisPerspective.BUSINESS_UNIT).get(0);
        assertEquals("南昌", source.getBaseCode());
        assertEquals("乘用车", source.getProductCode());
        assertEquals("一线", source.getLineCode());
        workbook.close();
    }

    @Test
    void includesTaskNoWhenReadingFails() {
        ImportFileRepository files = new ImportFileRepository() {
            @Override public void save(String taskNo, String fileName, byte[] content) { }
            @Override public InputStream open(String taskNo) throws java.io.IOException {
                throw new java.io.IOException("missing file");
            }
        };
        PoiPnlFactSourceReader reader = new PoiPnlFactSourceReader(files);
        ImportTask task = task();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> reader.readSources(batch(AnalysisPerspective.BASE), task));

        assertEquals(true, error.getMessage().contains("IMP-SOURCE-1"));
    }

    private List<PnlFactSource> read(XSSFWorkbook workbook, AnalysisPerspective perspective) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        PoiPnlFactSourceReader reader = new PoiPnlFactSourceReader(new MemoryFileRepository(output.toByteArray()));
        return reader.readSources(batch(perspective), task());
    }

    private XSSFWorkbook workbook(String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet(sheetName);
        return workbook;
    }

    private void headers(org.apache.poi.ss.usermodel.Sheet sheet, String... values) {
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < values.length; i++) header.createCell(i).setCellValue(values[i]);
    }

    private void row(org.apache.poi.ss.usermodel.Sheet sheet, int index, String... values) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private AnalysisBatch batch(AnalysisPerspective perspective) {
        return new AnalysisBatch(1L, "B-SOURCE-1", "2026-09", perspective,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now());
    }

    private ImportTask task() {
        return new ImportTask(1L, "IMP-SOURCE-1", "B-SOURCE-1", "BASE_ACT_INC_COST",
                "actual.xlsx", "sha-256", "tester", LocalDateTime.now());
    }

    private static class MemoryFileRepository implements ImportFileRepository {
        private final byte[] content;

        private MemoryFileRepository(byte[] content) {
            this.content = content;
        }

        @Override public void save(String taskNo, String fileName, byte[] value) { }

        @Override public InputStream open(String taskNo) {
            return new ByteArrayInputStream(content);
        }
    }
}
