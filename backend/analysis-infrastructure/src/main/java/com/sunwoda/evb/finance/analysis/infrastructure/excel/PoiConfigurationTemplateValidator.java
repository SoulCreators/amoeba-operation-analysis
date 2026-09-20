package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationTemplateValidator;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * First-stage configuration validation. It checks workbook structure only;
 * type-specific field mapping and application are intentionally separate.
 */
@Repository
public class PoiConfigurationTemplateValidator implements ConfigurationTemplateValidator {
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    @Override
    public ImportValidationResult validate(String taskNo, ConfigurationImportType type,
                                           InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        List<String> headers = new ArrayList<String>();
        int rowCount = 0;
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                issues.add(new ImportIssue(1, null, "SHEET_MISSING", "配置文件至少需要一个工作表", null));
            } else {
                Sheet sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(0);
                if (header == null) {
                    issues.add(new ImportIssue(1, null, "HEADER_MISSING", "Excel第一行必须为配置字段标题", null));
                } else {
                    validateHeaders(header, headers, issues);
                    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        if (row != null && !isBlank(row)) rowCount++;
                    }
                    if (rowCount == 0) issues.add(new ImportIssue(2, null, "DATA_MISSING", "配置文件至少需要一行数据", null));
                }
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "FILE_INVALID",
                    "配置文件无法读取或不是受支持的Excel格式: " + ex.getMessage(), null));
        }
        ImportStatus status = issues.isEmpty() ? ImportStatus.VALID : ImportStatus.INVALID;
        return new ImportValidationResult(taskNo, status, rowCount, headers, issues);
    }

    private void validateHeaders(Row row, List<String> headers, List<ImportIssue> issues) {
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String value = value(row.getCell(i));
            headers.add(value);
            if (value.isEmpty()) {
                issues.add(new ImportIssue(1, null, "HEADER_EMPTY", "配置字段标题不能为空", null));
                continue;
            }
            String normalized = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                issues.add(new ImportIssue(1, value, "DUPLICATE_HEADER", "配置字段标题重复", value));
            }
        }
    }

    private boolean isBlank(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) if (!value(row.getCell(i)).isEmpty()) return false;
        return true;
    }

    private String value(Cell cell) { return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
}
