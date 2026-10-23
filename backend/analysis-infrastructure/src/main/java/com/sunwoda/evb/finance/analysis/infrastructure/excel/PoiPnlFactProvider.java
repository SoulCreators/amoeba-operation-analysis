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
import org.apache.poi.ss.usermodel.CellType;
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
            Sheet sheet = PoiSheetLocator.locate(workbook, task.getDatasetCode());
            Row header = sheet.getRow(0);
            if (header == null) return;
            Map<String, Integer> columns = headers(header);
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (row == null) continue;
                if (perspective == AnalysisPerspective.BASE && excluded(row, columns)) continue;
                String scope = scope(row, columns, perspective);
                if (scope.isEmpty()) continue;
                String baseCode = firstText(row, columns, "收入计算基地", "基地", "出货基地");
                String productCode = firstText(row, columns, "电芯型号", "产品线", "产品类别", "客户项目改", "客户项目");
                String lineCode = firstText(row, columns, "拉线", "线体", "产线");
                String key = factKey(scope, baseCode, productCode, lineCode);
                Accumulator accumulator = accumulators.get(key);
                if (accumulator == null) {
                    accumulator = new Accumulator(scope, baseCode, productCode, lineCode);
                    accumulators.put(key, accumulator);
                }
                String code = task.getDatasetCode();
                if (isRevenueCost(code, perspective)) {
                    if (isBudget(code)) {
                        accumulator.budgetVolume = add(accumulator.budgetVolume,
                                budgetVolume(row, columns, "预算销量", "预算电量", "预算数量", "销量", "电量", "数量"));
                        accumulator.budgetOutputWh = addNullable(accumulator.budgetOutputWh,
                                numberOrNull(row, columns, "Wh", "产出Wh", "产出电量Wh"));
                        accumulator.budgetRevenue = add(accumulator.budgetRevenue, budgetAmount(row, columns,
                                "预算营业收入", "营业收入", "销售收入", "收入"));
                        accumulator.budgetSalesCost = add(accumulator.budgetSalesCost, budgetAmount(row, columns,
                                "预算销售成本", "销售成本", "成本"));
                        accumulator.budgetMaterialCost = add(accumulator.budgetMaterialCost, budgetAmount(row, columns,
                                "预算材料成本", "材料成本"));
                        accumulator.budgetMaterialLoss = addNullable(accumulator.budgetMaterialLoss,
                                budgetAmountOrNull(row, columns, "预算材料损耗", "材料损耗"));
                        accumulator.budgetLaborCost = add(accumulator.budgetLaborCost, budgetAmount(row, columns,
                                "预算直接人工", "直接人工", "人工成本"));
                        accumulator.budgetOutsourcingCost = add(accumulator.budgetOutsourcingCost, budgetAmount(row, columns,
                                "预算外协加工成本", "外协加工成本", "外协成本"));
                        accumulator.budgetVariableManufacturingCost = add(accumulator.budgetVariableManufacturingCost,
                                budgetAmount(row, columns, "预算制造成本-变动", "制造成本-变动", "制造费用-变动", "变动制费"));
                        accumulator.budgetFixedManufacturingCost = add(accumulator.budgetFixedManufacturingCost,
                                budgetAmount(row, columns, "预算制造成本-固定", "制造成本-固定", "制造费用-固定", "固定制费"));
                    } else {
                        accumulator.volume = add(accumulator.volume, number(row, columns, "销量", "电量", "数量"));
                        accumulator.outputWh = addNullable(accumulator.outputWh,
                                numberOrNull(row, columns, "Wh", "产出Wh", "产出电量Wh"));
                        accumulator.revenue = add(accumulator.revenue, number(row, columns, "营业收入", "销售收入", "收入"));
                        accumulator.salesCost = add(accumulator.salesCost, number(row, columns, "销售成本", "成本"));
                        accumulator.materialCost = add(accumulator.materialCost, number(row, columns, "材料成本"));
                        accumulator.materialLoss = addNullable(accumulator.materialLoss,
                                numberOrNull(row, columns, "材料损耗"));
                        accumulator.laborCost = add(accumulator.laborCost, number(row, columns, "直接人工", "人工成本"));
                        accumulator.outsourcingCost = add(accumulator.outsourcingCost, number(row, columns, "外协加工成本", "外协成本"));
                        accumulator.variableManufacturingCost = add(accumulator.variableManufacturingCost,
                                number(row, columns, "制造成本-变动", "制造费用-变动", "变动制费"));
                        accumulator.fixedManufacturingCost = add(accumulator.fixedManufacturingCost,
                                number(row, columns, "制造成本-固定", "制造费用-固定", "固定制费"));
                    }
                } else if (code.contains("IDLE")) {
                    accumulator.idleExpense = add(accumulator.idleExpense, number(row, columns, "金额", "闲置费用"));
                } else if (code.contains("L3_EXP") || code.contains("LAB_MFG_EXP")) {
                    routeL3Expense(accumulator, row, columns);
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

    private String factKey(String scope, String baseCode, String productCode, String lineCode) {
        return String.valueOf(scope) + "\u001f" + String.valueOf(baseCode)
                + "\u001f" + String.valueOf(productCode) + "\u001f" + String.valueOf(lineCode);
    }

    private boolean excluded(Row row, Map<String, Integer> columns) {
        String flag = firstText(row, columns, "不计算").trim().toLowerCase(Locale.ROOT);
        return "是".equals(flag) || "1".equals(flag) || "y".equals(flag)
                || "yes".equals(flag) || "true".equals(flag);
    }

    /** 三级费用按费用分类归集：事业部销售/管理费用进对应字段，其余保留基地费用口径。 */
    private void routeL3Expense(Accumulator accumulator, Row row, Map<String, Integer> columns) {
        String category = firstText(row, columns, "费用分类");
        BigDecimal amount = number(row, columns, "金额", "费用金额");
        if ("销售费用".equals(category)) {
            accumulator.salesExpense = add(accumulator.salesExpense, amount);
        } else if ("管理费用".equals(category)) {
            accumulator.adminExpense = add(accumulator.adminExpense, amount);
        } else {
            accumulator.baseExpense = add(accumulator.baseExpense, amount);
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
        BigDecimal value = numberOrNull(row, columns, names);
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal budgetAmount(Row row, Map<String, Integer> columns, String... names) {
        return number(row, columns, names).movePointRight(4);
    }

    private BigDecimal budgetAmountOrNull(Row row, Map<String, Integer> columns, String... names) {
        BigDecimal value = numberOrNull(row, columns, names);
        return value == null ? null : value.movePointRight(4);
    }

    private BigDecimal budgetVolume(Row row, Map<String, Integer> columns, String... names) {
        return number(row, columns, names).movePointRight(6);
    }

    private BigDecimal numberOrNull(Row row, Map<String, Integer> columns, String... names) {
        Cell cell = firstCell(row, columns, names);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String value = text(cell);
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value.replace(",", "").replace("(", "-").replace(")", ""));
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("数值字段无法转换: " + value, ex);
        }
    }

    private Cell firstCell(Row row, Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(normalize(name));
            if (index != null) {
                Cell cell = row.getCell(index);
                if (cell != null && !text(cell).isEmpty()) return cell;
            }
        }
        return null;
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        return left.add(right);
    }

    private BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.add(right);
    }

    private String text(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static class Accumulator {
        private final String scopeCode;
        private final String baseCode;
        private final String productCode;
        private final String lineCode;
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
        private BigDecimal materialLoss;
        private BigDecimal budgetMaterialLoss;
        private BigDecimal outputWh;
        private BigDecimal budgetOutputWh;
        private BigDecimal salesExpense = BigDecimal.ZERO;
        private BigDecimal adminExpense = BigDecimal.ZERO;

        private Accumulator(String scopeCode, String baseCode, String productCode, String lineCode) {
            this.scopeCode = scopeCode;
            this.baseCode = baseCode;
            this.productCode = productCode;
            this.lineCode = lineCode;
        }

        private PnlFact toFact(String ignoredScope) {
            return new PnlFact(scopeCode, baseCode, productCode, lineCode, volume, revenue, salesCost, idleExpense, baseExpense,
                    rdExpense, assetImpairment, creditImpairment, otherIncome,
                    budgetVolume, budgetRevenue, budgetSalesCost, budgetIdleExpense,
                    budgetBaseExpense, budgetRdExpense, budgetAssetImpairment,
                    budgetCreditImpairment, budgetOtherIncome, materialCost, laborCost,
                    outsourcingCost, variableManufacturingCost, fixedManufacturingCost,
                    budgetMaterialCost, budgetLaborCost, budgetOutsourcingCost,
                    budgetVariableManufacturingCost, budgetFixedManufacturingCost,
                    outputWh, budgetOutputWh, materialLoss, budgetMaterialLoss,
                    BigDecimal.ZERO, salesExpense, adminExpense,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
