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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * First-stage configuration validation. It checks workbook structure only;
 * type-specific field mapping and application are intentionally separate.
 */
@Repository
public class PoiConfigurationTemplateValidator implements ConfigurationTemplateValidator {
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);
    private final Map<ConfigurationImportType, List<List<String>>> requiredHeaderGroups = requiredHeaderGroups();

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
                    validateRequiredHeaders(type, headers, issues);
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

    /**
     * The templates in templates/import/20260921_最终模板 use business field
     * names, not the generic matchKey/mappedValue pair described by the first
     * API draft. Each inner list is an alias group; one header from a group is
     * enough for backwards compatibility with the earlier generic templates.
     */
    private Map<ConfigurationImportType, List<List<String>>> requiredHeaderGroups() {
        Map<ConfigurationImportType, List<List<String>>> result =
                new EnumMap<ConfigurationImportType, List<List<String>>>(ConfigurationImportType.class);
        result.put(ConfigurationImportType.METRIC_DEFINITION, Arrays.asList(
                aliases("metriccode", "指标编码", "编码"),
                aliases("metricname", "指标名称", "名称"),
                aliases("formulaexpression", "计算公式", "公式", "指标公式")));
        result.put(ConfigurationImportType.SALES_PROJECT_MAPPING, Arrays.asList(
                aliases("客户简称"), aliases("产品线"), aliases("事业部"), aliases("是否有效")));
        result.put(ConfigurationImportType.INVENTORY_MAPPING, Arrays.asList(
                aliases("物料编码"), aliases("电芯型号"), aliases("产品线"), aliases("事业部"), aliases("是否有效")));
        result.put(ConfigurationImportType.EXPENSE_CATEGORY, Arrays.asList(
                aliases("三级费用名称"), aliases("费用分类"), aliases("是否有效")));
        result.put(ConfigurationImportType.EXPENSE_ALLOCATION, Arrays.asList(
                aliases("期间"), aliases("费用项目"), aliases("来源基地"), aliases("目标基地"),
                aliases("分摊比例"), aliases("是否有效")));
        result.put(ConfigurationImportType.FX_RATE, Arrays.asList(
                aliases("期间"), aliases("基地"), aliases("币种"), aliases("汇率"),
                aliases("换算方向"), aliases("是否有效")));
        result.put(ConfigurationImportType.BASE_GRAIN, Arrays.asList(
                aliases("基地"), aliases("电芯型号"), aliases("分析粒度"), aliases("是否有效")));
        result.put(ConfigurationImportType.USER_PERMISSION, Arrays.asList(
                aliases("姓名"), aliases("工号"), aliases("用户角色"), aliases("数据视角"),
                aliases("可查看范围"), aliases("明细查看权限"), aliases("数据导出权限"), aliases("是否有效")));
        return result;
    }

    private List<String> aliases(String... values) {
        return Arrays.asList(values);
    }

    private void validateRequiredHeaders(ConfigurationImportType type, List<String> headers,
                                         List<ImportIssue> issues) {
        List<List<String>> groups = requiredHeaderGroups.get(type);
        if (groups == null) return;
        Set<String> normalizedHeaders = new HashSet<String>();
        for (String header : headers) normalizedHeaders.add(normalize(header));
        boolean genericMapping = hasAny(normalizedHeaders, "matchkey", "匹配键", "组合键", "来源值")
                && hasAny(normalizedHeaders, "mappedvalue", "映射值", "目标值");
        if (genericMapping && type != ConfigurationImportType.METRIC_DEFINITION
                && type != ConfigurationImportType.USER_PERMISSION) return;
        for (List<String> group : groups) {
            boolean present = false;
            for (String alias : group) {
                if (normalizedHeaders.contains(normalize(alias))) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                issues.add(new ImportIssue(1, group.get(0), "FIELD_MISSING",
                        "配置文件缺少必需字段: " + group.get(0), null));
            }
        }
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
            String normalized = normalize(value);
            if (!seen.add(normalized)) {
                issues.add(new ImportIssue(1, value, "DUPLICATE_HEADER", "配置字段标题重复", value));
            }
        }
    }

    private boolean isBlank(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) if (!value(row.getCell(i)).isEmpty()) return false;
        return true;
    }

    private boolean hasAny(Set<String> normalizedHeaders, String... aliases) {
        for (String alias : aliases) {
            if (normalizedHeaders.contains(normalize(alias))) return true;
        }
        return false;
    }

    private String value(Cell cell) { return cell == null ? "" : formatter.formatCellValue(cell).trim(); }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
