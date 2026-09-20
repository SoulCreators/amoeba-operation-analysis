package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoiPnlFactProviderTest {
    @Test
    void loadsBaseRevenueAndCostFromValidatedImport() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("实际");
        String[] headers = {"收入计算基地", "电芯型号", "财经修正项目", "销量", "营业收入", "销售成本"};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("南昌");
        row.createCell(1).setCellValue("106Ah");
        row.createCell(2).setCellValue("项目A");
        row.createCell(3).setCellValue(100);
        row.createCell(4).setCellValue(1000);
        row.createCell(5).setCellValue(700);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportTask task = new ImportTask(1L, "IMP-1", "B-1", "BASE_ACT_INC_COST",
                "actual.xlsx", null, "tester", LocalDateTime.now());
        task.moveTo(ImportStatus.VALID, 0);
        PnlFactProviderHolder provider = new PnlFactProviderHolder(task, output.toByteArray());
        List<PnlFact> facts = provider.load(new AnalysisBatch(1L, "B-1", "2026-09",
                AnalysisPerspective.BASE, BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));

        assertEquals(1, facts.size());
        assertEquals("南昌", facts.get(0).getScopeCode());
        assertEquals(1000, facts.get(0).getRevenue().intValue());
        assertEquals(700, facts.get(0).getSalesCost().intValue());
    }

    private static class PnlFactProviderHolder extends PoiPnlFactProvider {
        PnlFactProviderHolder(ImportTask task, byte[] content) {
            super(new TaskRepository(task), new FileRepository(content));
        }
    }

    private static class TaskRepository implements ImportTaskRepository {
        private final ImportTask task;
        TaskRepository(ImportTask task) { this.task = task; }
        @Override public ImportTask save(ImportTask value) { return value; }
        @Override public Optional<ImportTask> findByTaskNo(String taskNo) { return Optional.of(task); }
        @Override public boolean existsByBatchAndDataset(String batchNo, String datasetCode) { return true; }
        @Override public List<ImportTask> findByBatchNo(String batchNo) { return Arrays.asList(task); }
    }

    private static class FileRepository implements ImportFileRepository {
        private final byte[] content;
        FileRepository(byte[] content) { this.content = content; }
        @Override public void save(String taskNo, String fileName, byte[] value) { }
        @Override public InputStream open(String taskNo) { return new ByteArrayInputStream(content); }
    }
}
