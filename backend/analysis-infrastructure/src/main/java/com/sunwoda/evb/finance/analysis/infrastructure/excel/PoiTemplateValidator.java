package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.TemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetFieldDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class PoiTemplateValidator implements TemplateValidator {
    private static final int MAX_ISSUES = 1000;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    @Override
    public ImportValidationResult validate(String taskNo, DatasetDefinition definition,
                                           InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        List<String> headers = new ArrayList<String>();
        int rowCount = 0;
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                issues.add(new ImportIssue(1, null, "HEADER_MISSING", "Excel第一行必须为字段标题", null));
            } else {
                Map<String, Integer> headerIndex = readHeaders(headerRow, headers, issues);
                validateRequiredHeaders(definition, headerIndex, issues);
                rowCount = validateRows(sheet, definition, headerIndex, issues);
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "FILE_INVALID",
                    "文件无法读取或不是受支持的Excel格式: " + ex.getMessage(), null));
        }
        ImportStatus status = issues.isEmpty() ? ImportStatus.VALID : ImportStatus.INVALID;
        return new ImportValidationResult(taskNo, status, rowCount, headers, issues);
    }

    private Map<String, Integer> readHeaders(Row row, List<String> headers,
                                              List<ImportIssue> issues) {
        Map<String, Integer> headerIndex = new LinkedHashMap<String, Integer>();
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            String header = value(row.getCell(cellIndex));
            headers.add(header);
            if (header.isEmpty()) continue;
            String normalized = normalize(header);
            if (headerIndex.containsKey(normalized)) {
                issues.add(new ImportIssue(1, header, "DUPLICATE_HEADER",
                        "字段标题重复，请保留一列: " + header, header));
            } else {
                headerIndex.put(normalized, cellIndex);
            }
        }
        return headerIndex;
    }

    private void validateRequiredHeaders(DatasetDefinition definition,
                                         Map<String, Integer> headerIndex,
                                         List<ImportIssue> issues) {
        for (DatasetFieldDefinition field : definition.getFields()) {
            if (field.isRequired() && !headerIndex.containsKey(normalize(field.getFieldName()))) {
                issues.add(new ImportIssue(1, field.getFieldName(), "FIELD_MISSING",
                        "缺少必填字段: " + field.getFieldName(), null));
            }
            if (issues.size() >= MAX_ISSUES) return;
        }
    }

    private int validateRows(Sheet sheet, DatasetDefinition definition,
                             Map<String, Integer> headerIndex,
                             List<ImportIssue> issues) {
        int rowCount = 0;
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row)) continue;
            rowCount++;
            for (DatasetFieldDefinition field : definition.getFields()) {
                if (!field.isRequired()) continue;
                Integer cellIndex = headerIndex.get(normalize(field.getFieldName()));
                if (cellIndex == null) continue;
                String rawValue = value(row.getCell(cellIndex));
                if (rawValue.isEmpty()) {
                    issues.add(new ImportIssue(rowIndex + 1, field.getFieldName(), "FIELD_EMPTY",
                            "必填字段不能为空", rawValue));
                } else {
                    validateType(rowIndex + 1, field, rawValue, issues);
                }
                if (issues.size() >= MAX_ISSUES) return rowCount;
            }
        }
        return rowCount;
    }

    private void validateType(int rowNo, DatasetFieldDefinition field, String rawValue,
                              List<ImportIssue> issues) {
        if ("DECIMAL".equalsIgnoreCase(field.getDataType())) {
            try {
                new BigDecimal(rawValue.replace(",", ""));
            } catch (NumberFormatException ex) {
                issues.add(new ImportIssue(rowNo, field.getFieldName(), "TYPE_INVALID",
                        "金额字段必须为数字", rawValue));
            }
        } else if ("MONTH".equalsIgnoreCase(field.getDataType())
                && !rawValue.matches("20\\d{2}[-/]((0[1-9])|(1[0-2]))")) {
            issues.add(new ImportIssue(rowNo, field.getFieldName(), "TYPE_INVALID",
                    "期间字段必须为YYYY-MM格式", rawValue));
        }
    }

    private boolean isBlankRow(Row row) {
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            if (!value(row.getCell(cellIndex)).isEmpty()) return false;
        }
        return true;
    }

    private String value(Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
