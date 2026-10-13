package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotTemplateParser;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshotImportPayload;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 解析 FC 快照标准模板：期间、范围编码、指标编码、预测值。 */
@Component
public class PoiFcSnapshotTemplateParser implements FcSnapshotTemplateParser {
    private static final Set<String> METRICS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "VOLUME", "REVENUE", "GROSS_PROFIT", "BASE_EXPENSE", "NET_PROFIT", "PROFIT_RATE")));

    @Override
    public FcSnapshotImportPayload parse(InputStream inputStream, String taskNo) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        List<FcSnapshot> snapshots = new ArrayList<FcSnapshot>();
        List<String> headers = new ArrayList<String>();
        int rowCount = 0;
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                issues.add(issue(0, "文件", "NO_SHEET", "工作簿没有工作表", ""));
                return new FcSnapshotImportPayload(taskNo, 0, headers, snapshots, issues);
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                issues.add(issue(1, "表头", "MISSING_HEADER", "第一个工作表缺少表头", ""));
                return new FcSnapshotImportPayload(taskNo, 0, headers, snapshots, issues);
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = readHeaders(headerRow, formatter, headers);
            requireColumn(columns, "period", "期间", issues);
            requireColumn(columns, "scopeCode", "范围编码", issues);
            requireColumn(columns, "metricCode", "指标编码", issues);
            requireColumn(columns, "metricValue", "预测值", issues);
            if (!issues.isEmpty()) {
                return new FcSnapshotImportPayload(taskNo, 0, headers, snapshots, issues);
            }
            Set<String> keys = new HashSet<String>();
            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isBlank(row, formatter)) continue;
                rowCount++;
                String period = text(row, columns.get("period"), formatter);
                String scopeCode = text(row, columns.get("scopeCode"), formatter);
                String metricCode = text(row, columns.get("metricCode"), formatter).toUpperCase();
                String metricValue = text(row, columns.get("metricValue"), formatter);
                if (!period.matches("\\d{4}-\\d{2}")) {
                    issues.add(issue(i + 1, "期间", "INVALID_PERIOD", "期间必须为 YYYY-MM", period));
                } else {
                    try {
                        int month = Integer.parseInt(period.substring(5));
                        if (month < 1 || month > 12) {
                            issues.add(issue(i + 1, "期间", "INVALID_PERIOD", "期间月份必须为 01-12", period));
                        }
                    } catch (NumberFormatException ex) {
                        issues.add(issue(i + 1, "期间", "INVALID_PERIOD", "期间格式无效", period));
                    }
                }
                if (scopeCode.isEmpty() || scopeCode.length() > 128) {
                    issues.add(issue(i + 1, "范围编码", "INVALID_SCOPE", "范围编码不能为空且不超过128个字符", scopeCode));
                }
                if (!METRICS.contains(metricCode)) {
                    issues.add(issue(i + 1, "指标编码", "INVALID_METRIC", "指标编码不在支持范围内", metricCode));
                }
                BigDecimal value = parseNumber(metricValue);
                if (value == null) {
                    issues.add(issue(i + 1, "预测值", "INVALID_NUMBER", "预测值必须为有效数字", metricValue));
                }
                String key = period + "\u001f" + scopeCode + "\u001f" + metricCode;
                if (!keys.add(key)) {
                    issues.add(issue(i + 1, "行", "DUPLICATE_KEY", "期间、范围编码、指标编码组合重复", key));
                }
                if (rowValid(period, scopeCode, metricCode, value)) {
                    snapshots.add(new FcSnapshot(period, scopeCode, metricCode, value));
                }
            }
        } catch (Exception ex) {
            issues.add(issue(0, "文件", "INVALID_WORKBOOK", "无法读取 FC 快照工作簿: " + ex.getMessage(), ""));
        }
        return new FcSnapshotImportPayload(taskNo, rowCount, headers, snapshots, issues);
    }

    private Map<String, Integer> readHeaders(Row row, DataFormatter formatter, List<String> headers) {
        Map<String, Integer> columns = new HashMap<String, Integer>();
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (i < 0) continue;
            String header = text(row, i, formatter);
            headers.add(header);
            String normalized = normalize(header);
            if ("期间".equals(normalized) || "period".equalsIgnoreCase(normalized)) columns.put("period", i);
            if ("范围编码".equals(normalized) || "范围".equals(normalized)
                    || "scopecode".equalsIgnoreCase(normalized)) columns.put("scopeCode", i);
            if ("指标编码".equals(normalized) || "指标".equals(normalized)
                    || "metriccode".equalsIgnoreCase(normalized)) columns.put("metricCode", i);
            if ("预测值".equals(normalized) || "值".equals(normalized)
                    || "metricvalue".equalsIgnoreCase(normalized)) columns.put("metricValue", i);
        }
        return columns;
    }

    private void requireColumn(Map<String, Integer> columns, String key, String name, List<ImportIssue> issues) {
        if (!columns.containsKey(key)) issues.add(issue(1, name, "MISSING_HEADER", "缺少必填列: " + name, ""));
    }

    private boolean rowValid(String period, String scopeCode, String metricCode, BigDecimal value) {
        if (!period.matches("\\d{4}-\\d{2}")) return false;
        int month = Integer.parseInt(period.substring(5));
        return month >= 1 && month <= 12 && !scopeCode.isEmpty() && scopeCode.length() <= 128
                && METRICS.contains(metricCode) && value != null;
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (i >= 0 && !text(row, i, formatter).isEmpty()) return false;
        }
        return true;
    }

    private String text(Row row, Integer index, DataFormatter formatter) {
        return index == null ? "" : text(row, index.intValue(), formatter);
    }

    private String text(Row row, int index, DataFormatter formatter) {
        Cell cell = row == null ? null : row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace(" ", "").replace("　", "");
    }

    private BigDecimal parseNumber(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim().replace(",", "").replace("，", "");
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        if (negative) normalized = normalized.substring(1, normalized.length() - 1);
        try {
            BigDecimal number = new BigDecimal(normalized);
            return negative ? number.negate() : number;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ImportIssue issue(int rowNo, String field, String code, String message, String raw) {
        return new ImportIssue(rowNo, field, code, message, raw);
    }
}
