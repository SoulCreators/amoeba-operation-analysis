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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PoiPnlFactProviderTest {
    @Test
    void readsBudgetColumnsFromPublishedImportTemplate() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("预算");
        String[] headers = {"收入计算基地", "预算电量", "预算营业收入", "预算销售成本",
                "预算材料成本", "预算材料损耗", "预算直接人工", "预算外协加工成本",
                "预算制造成本-变动", "预算制造成本-固定"};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("南京");
        for (int i = 1; i < headers.length; i++) row.createCell(i).setCellValue(i * 10);
        row.getCell(2).setCellValue(20.12345678);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportTask task = validatedTask(4L, "IMP-BUDGET-TEMPLATE", "BASE_BUD_INC_COST");
        PnlFact fact = new PnlFactProviderHolder(Arrays.asList(task), output.toByteArray())
                .read(batch(), task).get(0);
        assertEquals(10_000_000, fact.getBudgetVolume().intValue());
        assertEquals(0, new java.math.BigDecimal("201234.5678").compareTo(fact.getBudgetRevenue()));
        assertEquals(300_000, fact.getBudgetSalesCost().intValue());
        assertEquals(400_000, fact.getBudgetMaterialCost().intValue());
        assertEquals(500_000, fact.getBudgetMaterialLoss().intValue());
        assertEquals(600_000, fact.getBudgetLaborCost().intValue());
        assertEquals(700_000, fact.getBudgetOutsourcingCost().intValue());
        assertEquals(800_000, fact.getBudgetVariableManufacturingCost().intValue());
        assertEquals(900_000, fact.getBudgetFixedManufacturingCost().intValue());
    }

    @Test
    void retainsActualAndBudgetMaterialLossSeparately() throws Exception {
        ImportTask actualTask = validatedTask(1L, "IMP-ACTUAL", "BASE_ACT_INC_COST");
        ImportTask budgetTask = validatedTask(2L, "IMP-BUDGET", "BASE_BUD_INC_COST");
        PnlFact actual = new PnlFactProviderHolder(Arrays.asList(actualTask),
                workbookBytes("材料损耗", 12.5)).read(batch(), actualTask).get(0);
        PnlFact budget = new PnlFactProviderHolder(Arrays.asList(budgetTask),
                workbookBytes("预算材料损耗", 8.25)).read(batch(), budgetTask).get(0);

        assertEquals(12.5, actual.getMaterialLoss().doubleValue(), 0.000001);
        org.junit.jupiter.api.Assertions.assertNull(actual.getBudgetMaterialLoss());
        assertEquals(82500, budget.getBudgetMaterialLoss().doubleValue(), 0.000001);
        org.junit.jupiter.api.Assertions.assertNull(budget.getMaterialLoss());
        assertEquals(700, actual.getSalesCost().intValue());
        assertEquals(7_000_000, budget.getBudgetSalesCost().intValue());
    }

    @Test
    void distinguishesMissingMaterialLossFromExplicitZero() throws Exception {
        ImportTask actualTask = validatedTask(3L, "IMP-ACTUAL-NULL", "BASE_ACT_INC_COST");
        PnlFact missing = new PnlFactProviderHolder(Arrays.asList(actualTask),
                workbookBytes("材料损耗", null)).read(batch(), actualTask).get(0);
        PnlFact explicitZero = new PnlFactProviderHolder(Arrays.asList(actualTask),
                workbookBytes("材料损耗", 0d)).read(batch(), actualTask).get(0);

        org.junit.jupiter.api.Assertions.assertNull(missing.getMaterialLoss());
        assertEquals(0, explicitZero.getMaterialLoss().signum());
    }

    @Test
    void loadsBaseRevenueAndCostFromValidatedImport() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("实际");
        String[] headers = {"收入计算基地", "电芯型号", "财经修正项目", "销量", "营业收入", "销售成本", "产出Wh", "不计算"};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("南昌");
        row.createCell(1).setCellValue("106Ah");
        row.createCell(2).setCellValue("项目A");
        row.createCell(3).setCellValue(100);
        row.createCell(4).setCellValue(1000);
        row.createCell(5).setCellValue(700);
        row.createCell(6).setCellValue(100);
        org.apache.poi.ss.usermodel.Row excluded = sheet.createRow(2);
        excluded.createCell(0).setCellValue("南昌");
        excluded.createCell(3).setCellValue(999);
        excluded.createCell(4).setCellValue(999);
        excluded.createCell(7).setCellValue("是");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportTask task = new ImportTask(1L, "IMP-1", "B-1", "BASE_ACT_INC_COST",
                "actual.xlsx", null, "tester", LocalDateTime.now());
        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.VALID, 0);
        ImportTask budgetTask = new ImportTask(2L, "IMP-2", "B-1", "BASE_BUD_INC_COST",
                "budget.xlsx", null, "tester", LocalDateTime.now());
        budgetTask.moveTo(ImportStatus.VALIDATING, 0);
        budgetTask.moveTo(ImportStatus.VALID, 0);
        PnlFactProviderHolder provider = new PnlFactProviderHolder(Arrays.asList(task, budgetTask), output.toByteArray());
        List<PnlFact> facts = provider.load(new AnalysisBatch(1L, "B-1", "2026-09",
                AnalysisPerspective.BASE, BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));

        assertEquals(1, facts.size());
        assertEquals("南昌", facts.get(0).getScopeCode());
        assertEquals(1000, facts.get(0).getRevenue().intValue());
        assertEquals(700, facts.get(0).getSalesCost().intValue());
        assertEquals(10_000_000, facts.get(0).getBudgetRevenue().intValue());
        assertEquals(7_000_000, facts.get(0).getBudgetSalesCost().intValue());
        assertEquals("南昌", facts.get(0).getBaseCode());
        assertEquals("106Ah", facts.get(0).getProductCode());
        assertEquals(100, facts.get(0).getOutputWh().intValue());
        assertEquals(100, facts.get(0).getBudgetOutputWh().intValue());
    }

    @Test
    void locatesIncomeCostSheetByNameInMultiSheetWorkbook() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet summary = workbook.createSheet("导出摘要");
        summary.createRow(0).createCell(0).setCellValue("与损益无关的摘要");
        org.apache.poi.ss.usermodel.Sheet data = workbook.createSheet("2.6收入成本明细-实际");
        String[] headers = {"产品线", "销售收入", "销售成本", "材料成本", "直接人工", "外协费用", "制造费用-变动", "制造费用-固定", "电量"};
        org.apache.poi.ss.usermodel.Row header = data.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = data.createRow(1);
        row.createCell(0).setCellValue("磷酸盐");
        row.createCell(1).setCellValue(1000);
        row.createCell(2).setCellValue(700);
        row.createCell(3).setCellValue(500);
        row.createCell(4).setCellValue(60);
        row.createCell(5).setCellValue(20);
        row.createCell(6).setCellValue(30);
        row.createCell(7).setCellValue(10);
        row.createCell(8).setCellValue(100);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportTask task = validatedTask(5L, "IMP-PLBU-ACT", "PLBU_ACT_INC_COST");
        PnlFact fact = new PnlFactProviderHolder(Arrays.asList(task), output.toByteArray())
                .read(batch(AnalysisPerspective.PRODUCT_LINE), task).get(0);

        assertEquals("磷酸盐", fact.getScopeCode());
        assertEquals("磷酸盐", fact.getProductCode());
        assertEquals(1000, fact.getRevenue().intValue());
        assertEquals(700, fact.getSalesCost().intValue());
        assertEquals(500, fact.getMaterialCost().intValue());
    }

    @Test
    void routesL3ExpenseByCategoryForBusinessUnit() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("2.17三级费用明细表");
        String[] headers = {"事业部", "费用分类", "金额"};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        String[][] rows = {
                {"储能", "销售费用", "40"},
                {"储能", "研发费用", "15"},
                {"储能", "管理费用", "20"},
                {"西北欧", "销售费用", "60"},
        };
        for (int r = 0; r < rows.length; r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(r + 1);
            row.createCell(0).setCellValue(rows[r][0]);
            row.createCell(1).setCellValue(rows[r][1]);
            row.createCell(2).setCellValue(Double.parseDouble(rows[r][2]));
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();

        ImportTask task = validatedTask(6L, "IMP-PLBU-L3", "PLBU_L3_EXP");
        List<PnlFact> facts = new PnlFactProviderHolder(Arrays.asList(task), output.toByteArray())
                .read(batch(AnalysisPerspective.BUSINESS_UNIT), task);

        PnlFact storage = facts.stream().filter(f -> "储能".equals(f.getScopeCode())).findFirst().orElseThrow(
                () -> new AssertionError("缺少储能事业部事实"));
        assertEquals(40, storage.getSalesExpense().intValue());
        assertEquals(20, storage.getAdminExpense().intValue());
        assertEquals(15, storage.getBaseExpense().intValue());
        assertEquals(0, storage.getOperatingExpense().intValue());
    }

    private static class PnlFactProviderHolder extends PoiPnlFactProvider {
        PnlFactProviderHolder(List<ImportTask> tasks, byte[] content) {
            super(new TaskRepository(tasks), new FileRepository(content));
        }
    }

    private static ImportTask validatedTask(long id, String taskNo, String datasetCode) {
        ImportTask task = new ImportTask(id, taskNo, "B-1", datasetCode,
                taskNo + ".xlsx", null, "tester", LocalDateTime.now());
        task.moveTo(ImportStatus.VALIDATING, 0);
        task.moveTo(ImportStatus.VALID, 0);
        return task;
    }

    private static AnalysisBatch batch() {
        return batch(AnalysisPerspective.BASE);
    }

    private static AnalysisBatch batch(AnalysisPerspective perspective) {
        return new AnalysisBatch(1L, "B-1", "2026-09", perspective,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now());
    }

    private static byte[] workbookBytes(String materialLossHeader, Double materialLoss) throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("实际");
        String[] headers = {"收入计算基地", "销量", "营业收入", "销售成本", "材料成本", materialLossHeader};
        org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(1);
        row.createCell(0).setCellValue("南昌");
        row.createCell(1).setCellValue(100);
        row.createCell(2).setCellValue(1000);
        row.createCell(3).setCellValue(700);
        row.createCell(4).setCellValue(500);
        if (materialLoss != null) row.createCell(5).setCellValue(materialLoss);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        return output.toByteArray();
    }

    private static class TaskRepository implements ImportTaskRepository {
        private final List<ImportTask> tasks;
        TaskRepository(List<ImportTask> tasks) { this.tasks = tasks; }
        @Override public ImportTask save(ImportTask value) { return value; }
        @Override public Optional<ImportTask> findByTaskNo(String taskNo) { return Optional.of(tasks.get(0)); }
        @Override public boolean existsByBatchAndDataset(String batchNo, String datasetCode) { return true; }
        @Override public List<ImportTask> findByBatchNo(String batchNo) { return tasks; }
    }

    private static class FileRepository implements ImportFileRepository {
        private final byte[] content;
        FileRepository(byte[] content) { this.content = content; }
        @Override public void save(String taskNo, String fileName, byte[] value) { }
        @Override public InputStream open(String taskNo) { return new ByteArrayInputStream(content); }
    }
}
