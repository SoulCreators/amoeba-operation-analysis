package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTaskReader;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportFileRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 将已校验的收入成本、费用和减值工作表标准化为统一损益事实。 */
@Repository
public class PoiPnlFactProvider implements PnlFactProvider, PnlFactTaskReader {
    private final ImportTaskRepository taskRepository;
    private final ImportFileRepository fileRepository;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public PoiPnlFactProvider(ImportTaskRepository taskRepository,
                              ImportFileRepository fileRepository) {
        this.taskRepository = taskRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public List<PnlFact> load(AnalysisBatch batch) {
        List<ImportTask> tasks = taskRepository.findByBatchNo(batch.getBatchNo());
        Map<String, Accumulator> accumulators = new LinkedHashMap<String, Accumulator>();
        boolean hasRevenueCost = false;
        for (ImportTask task : tasks) {
            if (task.getStatus() != ImportStatus.VALID) continue;
            if (isRevenueCost(task.getDatasetCode(), batch.getPerspective())
                    && !isBudget(task.getDatasetCode())) hasRevenueCost = true;
            readTask(task, batch.getPerspective(), accumulators);
        }
        if (!hasRevenueCost) {
            throw new IllegalStateException("批次缺少已校验的实际收入成本数据集");
        }
        java.util.List<PnlFact> facts = new java.util.ArrayList<PnlFact>();
        for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
            facts.add(entry.getValue().toFact(entry.getKey()));
        }
        return facts;
    }

    @Override
    public List<PnlFact> read(AnalysisBatch batch, ImportTask task) {
        Map<String, Accumulator> accumulators = new LinkedHashMap<String, Accumulator>();
        readTask(task, batch.getPerspective(), accumulators);
        java.util.List<PnlFact> facts = new java.util.ArrayList<PnlFact>();
        for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
            facts.add(entry.getValue().toFact(entry.getKey()));
        }
        return facts;
    }

    private void readTask(ImportTask task, AnalysisPerspective perspective,
                          Map<String, Accumulator> accumulators) {
        try (InputStream input = fileRepository.open(task.getTaskNo());
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return;
            Map<String, Integer> columns = headers(header);
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (row == null) continue;
                String scope = scope(row, columns, perspective);
                if (scope.isEmpty()) continue;
                Accumulator accumulator = accumulators.get(scope);
                if (accumulator == null) {
                    accumulator = new Accumulator();
                    accumulators.put(scope, accumulator);
                }
                String code = task.getDatasetCode();
                if (isRevenueCost(code, perspective)) {
                    if (isBudget(code)) {
                        accumulator.budgetVolume = add(accumulator.budgetVolume, number(row, columns, "销量", "电量", "数量"));
                        accumulator.budgetRevenue = add(accumulator.budgetRevenue, number(row, columns, "营业收入", "销售收入", "收入"));
                        accumulator.budgetSalesCost = add(accumulator.budgetSalesCost, number(row, columns, "销售成本", "成本"));
                        accumulator.budgetMaterialCost = add(accumulator.budgetMaterialCost, number(row, columns, "材料成本"));
                        accumulator.budgetLaborCost = add(accumulator.budgetLaborCost, number(row, columns, "直接人工", "人工成本"));
                        accumulator.budgetOutsourcingCost = add(accumulator.budgetOutsourcingCost, number(row, columns, "外协加工成本", "外协成本"));
                        accumulator.budgetVariableManufacturingCost = add(accumulator.budgetVariableManufacturingCost, number(row, columns, "制造成本-变动", "变动制费"));
                        accumulator.budgetFixedManufacturingCost = add(accumulator.budgetFixedManufacturingCost, number(row, columns, "制造成本-固定", "固定制费"));
                    } else {
                        accumulator.volume = add(accumulator.volume, number(row, columns, "销量", "电量", "数量"));
                        accumulator.revenue = add(accumulator.revenue, number(row, columns, "营业收入", "销售收入", "收入"));
                        accumulator.salesCost = add(accumulator.salesCost, number(row, columns, "销售成本", "成本"));
                        accumulator.materialCost = add(accumulator.materialCost, number(row, columns, "材料成本"));
                        accumulator.laborCost = add(accumulator.laborCost, number(row, columns, "直接人工", "人工成本"));
                        accumulator.outsourcingCost = add(accumulator.outsourcingCost, number(row, columns, "外协加工成本", "外协成本"));
                        accumulator.variableManufacturingCost = add(accumulator.variableManufacturingCost, number(row, columns, "制造成本-变动", "变动制费"));
                        accumulator.fixedManufacturingCost = add(accumulator.fixedManufacturingCost, number(row, columns, "制造成本-固定", "固定制费"));
                    }
                } else if (code.contains("IDLE")) {
                    accumulator.idleExpense = add(accumulator.idleExpense, number(row, columns, "金额", "闲置费用"));
                } else if (code.contains("L3_EXP") || code.contains("LAB_MFG_EXP")) {
                    accumulator.baseExpense = add(accumulator.baseExpense, number(row, columns, "金额", "费用金额"));
                } else if (code.contains("RD_EXP")) {
                    accumulator.rdExpense = add(accumulator.rdExpense, number(row, columns, "金额", "研发费用"));
                } else if (code.contains("IMPAIRMENT")) {
                    accumulator.assetImpairment = add(accumulator.assetImpairment,
                            number(row, columns, "资产减值损失", "资产减值", "金额"));
                    accumulator.creditImpairment = add(accumulator.creditImpairment,
                            number(row, columns, "信用减值损失", "信用减值"));
                }
                accumulator.otherIncome = add(accumulator.otherIncome, number(row, columns, "其他收益"));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("解析导入事实失败: " + task.getTaskNo() + ", " + ex.getMessage(), ex);
        }
    }

    private boolean isRevenueCost(String code, AnalysisPerspective perspective) {
        return "BASE_ACT_INC_COST".equals(code)
                || "BASE_BUD_INC_COST".equals(code)
                || "PLBU_ACT_INC_COST".equals(code)
                || "PLBU_BUD_INC_COST".equals(code);
    }

    private boolean isBudget(String code) {
        return code != null && code.toUpperCase(Locale.ROOT).contains("_BUD_");
    }

    private String scope(Row row, Map<String, Integer> columns, AnalysisPerspective perspective) {
        if (perspective == AnalysisPerspective.BASE) {
            return firstText(row, columns, "收入计算基地", "基地", "出货基地");
        }
        if (perspective == AnalysisPerspective.PRODUCT_LINE) {
            return firstText(row, columns, "产品线", "客户项目", "项目");
        }
        return firstText(row, columns, "事业部", "客户项目", "项目");
    }

    private Map<String, Integer> headers(Row row) {
        Map<String, Integer> result = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String text = text(row.getCell(i));
            if (!text.isEmpty()) result.put(normalize(text), i);
        }
        return result;
    }

    private String firstText(Row row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalize(name));
            if (index != null) {
                String value = text(row.getCell(index));
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private BigDecimal number(Row row, Map<String, Integer> columns, String... names) {
        String value = firstText(row, columns, names);
        if (value.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.replace(",", "").replace("(", "-").replace(")", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("数值字段无法转换: " + value, ex);
        }
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return left.add(right);
    }

    private String text(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static class Accumulator {
        private BigDecimal volume = BigDecimal.ZERO;
        private BigDecimal revenue = BigDecimal.ZERO;
        private BigDecimal salesCost = BigDecimal.ZERO;
        private BigDecimal idleExpense = BigDecimal.ZERO;
        private BigDecimal baseExpense = BigDecimal.ZERO;
        private BigDecimal rdExpense = BigDecimal.ZERO;
        private BigDecimal assetImpairment = BigDecimal.ZERO;
        private BigDecimal creditImpairment = BigDecimal.ZERO;
        private BigDecimal otherIncome = BigDecimal.ZERO;
        private BigDecimal budgetVolume = BigDecimal.ZERO;
        private BigDecimal budgetRevenue = BigDecimal.ZERO;
        private BigDecimal budgetSalesCost = BigDecimal.ZERO;
        private BigDecimal budgetIdleExpense = BigDecimal.ZERO;
        private BigDecimal budgetBaseExpense = BigDecimal.ZERO;
        private BigDecimal budgetRdExpense = BigDecimal.ZERO;
        private BigDecimal budgetAssetImpairment = BigDecimal.ZERO;
        private BigDecimal budgetCreditImpairment = BigDecimal.ZERO;
        private BigDecimal budgetOtherIncome = BigDecimal.ZERO;
        private BigDecimal materialCost = BigDecimal.ZERO;
        private BigDecimal laborCost = BigDecimal.ZERO;
        private BigDecimal outsourcingCost = BigDecimal.ZERO;
        private BigDecimal variableManufacturingCost = BigDecimal.ZERO;
        private BigDecimal fixedManufacturingCost = BigDecimal.ZERO;
        private BigDecimal budgetMaterialCost = BigDecimal.ZERO;
        private BigDecimal budgetLaborCost = BigDecimal.ZERO;
        private BigDecimal budgetOutsourcingCost = BigDecimal.ZERO;
        private BigDecimal budgetVariableManufacturingCost = BigDecimal.ZERO;
        private BigDecimal budgetFixedManufacturingCost = BigDecimal.ZERO;

        private PnlFact toFact(String scope) {
            return new PnlFact(scope, volume, revenue, salesCost, idleExpense, baseExpense,
                    rdExpense, assetImpairment, creditImpairment, otherIncome,
                    budgetVolume, budgetRevenue, budgetSalesCost, budgetIdleExpense,
                    budgetBaseExpense, budgetRdExpense, budgetAssetImpairment,
                    budgetCreditImpairment, budgetOtherIncome, materialCost, laborCost,
                    outsourcingCost, variableManufacturingCost, fixedManufacturingCost,
                    budgetMaterialCost, budgetLaborCost, budgetOutsourcingCost,
                    budgetVariableManufacturingCost, budgetFixedManufacturingCost);
        }
    }
}
