package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies the two low-risk configuration families used in phase one:
 * metric definitions and key/value mappings. Permission files are deliberately
 * not applied by this adapter until the administrator workflow is connected.
 */
@Repository
public class PoiConfigurationImportApplier implements ConfigurationImportApplier {
    private final MetricDefinitionRepository metricRepository;
    private final MappingEntryRepository mappingRepository;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public PoiConfigurationImportApplier(MetricDefinitionRepository metricRepository,
                                         MappingEntryRepository mappingRepository) {
        this.metricRepository = metricRepository;
        this.mappingRepository = mappingRepository;
    }

    @Override
    public ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream inputStream) {
        if (task.getType() == ConfigurationImportType.METRIC_DEFINITION) {
            return applyMetrics(task, inputStream);
        }
        MappingType mappingType = mappingType(task.getType());
        if (mappingType != null) return applyMappings(task, mappingType, inputStream);
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        issues.add(new ImportIssue(1, null, "TYPE_UNSUPPORTED",
                "当前版本暂不自动应用配置类型: " + task.getType().name(), null));
        return new ConfigurationApplyResult(0, issues);
    }

    private ConfigurationApplyResult applyMetrics(ConfigurationImportTask task, InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        List<MetricRow> rows = new ArrayList<MetricRow>();
        Set<String> codes = new HashSet<String>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                String code = first(row, headers, "metriccode", "指标编码", "编码");
                String name = first(row, headers, "metricname", "指标名称", "名称");
                String formula = first(row, headers, "formulaexpression", "公式", "指标公式");
                if (code.isEmpty() || name.isEmpty() || formula.isEmpty()) {
                    issues.add(new ImportIssue(rowNo + 1, null, "FIELD_MISSING",
                            "指标配置必须包含指标编码、指标名称和公式", null));
                    continue;
                }
                if (!codes.add(code)) {
                    issues.add(new ImportIssue(rowNo + 1, "指标编码", "DUPLICATE_KEY",
                            "同一文件内指标编码重复: " + code, code));
                    continue;
                }
                String perspective = defaultIfEmpty(first(row, headers, "applicableperspective", "适用视角"), "ALL");
                String unit = defaultIfEmpty(first(row, headers, "unit", "单位"), "CNY");
                int order = integer(first(row, headers, "displayorder", "排序"), rowNo * 10);
                boolean enabled = booleanValue(first(row, headers, "enabled", "是否启用"), true);
                String aiTemplate = first(row, headers, "aisummarytemplate", "AI摘要模板", "AI简析模板");
                rows.add(new MetricRow(code, name, perspective, unit, formula, order, enabled, aiTemplate));
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "APPLY_FAILED", "指标配置应用失败: " + ex.getMessage(), null));
        }
        if (!issues.isEmpty()) return new ConfigurationApplyResult(0, issues);
        for (MetricRow row : rows) {
            MetricDefinition definition = metricRepository.findByCode(row.code).orElse(null);
            if (definition == null) {
                definition = new MetricDefinition(null, row.code, row.name, row.perspective, row.unit, row.formula,
                        row.order, row.enabled, row.aiTemplate, task.getCreatedBy(), LocalDateTime.now());
            } else {
                definition.update(row.name, row.perspective, row.unit, row.formula, row.order, row.enabled,
                        row.aiTemplate, task.getCreatedBy());
            }
            metricRepository.save(definition);
        }
        return new ConfigurationApplyResult(rows.size(), issues);
    }

    private ConfigurationApplyResult applyMappings(ConfigurationImportTask task, MappingType type,
                                                   InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        List<MappingRow> rows = new ArrayList<MappingRow>();
        Set<String> keys = new HashSet<String>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                String key = first(row, headers, "matchkey", "匹配键", "组合键", "来源值");
                String value = first(row, headers, "mappedvalue", "映射值", "目标值");
                if (key.isEmpty() || value.isEmpty()) {
                    issues.add(new ImportIssue(rowNo + 1, null, "FIELD_MISSING",
                            "映射配置必须包含匹配键和映射值", null));
                    continue;
                }
                if (!keys.add(key)) {
                    issues.add(new ImportIssue(rowNo + 1, "匹配键", "DUPLICATE_KEY",
                            "同一文件内匹配键重复: " + key, key));
                    continue;
                }
                boolean enabled = booleanValue(first(row, headers, "enabled", "是否启用"), true);
                rows.add(new MappingRow(key, value, enabled));
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "APPLY_FAILED", "映射配置应用失败: " + ex.getMessage(), null));
        }
        if (!issues.isEmpty()) return new ConfigurationApplyResult(0, issues);
        for (MappingRow row : rows) {
            MappingEntry entry = mappingRepository.find(type, row.key).orElse(null);
            if (entry == null) {
                entry = new MappingEntry(null, type, row.key, row.value, row.enabled,
                        task.getCreatedBy(), LocalDateTime.now());
            } else {
                entry.update(row.value, row.enabled, task.getCreatedBy());
            }
            mappingRepository.save(entry);
        }
        return new ConfigurationApplyResult(rows.size(), issues);
    }

    private static final class MetricRow {
        private final String code;
        private final String name;
        private final String perspective;
        private final String unit;
        private final String formula;
        private final int order;
        private final boolean enabled;
        private final String aiTemplate;

        private MetricRow(String code, String name, String perspective, String unit, String formula,
                          int order, boolean enabled, String aiTemplate) {
            this.code = code;
            this.name = name;
            this.perspective = perspective;
            this.unit = unit;
            this.formula = formula;
            this.order = order;
            this.enabled = enabled;
            this.aiTemplate = aiTemplate;
        }
    }

    private static final class MappingRow {
        private final String key;
        private final String value;
        private final boolean enabled;

        private MappingRow(String key, String value, boolean enabled) {
            this.key = key;
            this.value = value;
            this.enabled = enabled;
        }
    }

    private MappingType mappingType(ConfigurationImportType type) {
        switch (type) {
            case SALES_PROJECT_MAPPING: return MappingType.SALES_PROJECT;
            case INVENTORY_MAPPING: return MappingType.INVENTORY;
            case EXPENSE_CATEGORY:
            case EXPENSE_ALLOCATION: return MappingType.EXPENSE_RULE;
            case FX_RATE: return MappingType.FX_RATE;
            case BASE_GRAIN: return MappingType.BASE_GRAIN;
            default: return null;
        }
    }

    private Map<String, Integer> headers(Row row) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        if (row == null) return result;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String header = value(row.getCell(i));
            if (!header.isEmpty()) result.put(normalize(header), i);
        }
        return result;
    }

    private String first(Row row, Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            Integer index = headers.get(normalize(alias));
            if (index != null) return value(row.getCell(index));
        }
        return "";
    }

    private boolean blank(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) if (!value(row.getCell(i)).isEmpty()) return false;
        return true;
    }

    private String value(Cell cell) { return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT); }
    private String defaultIfEmpty(String value, String defaultValue) { return value.isEmpty() ? defaultValue : value; }
    private int integer(String value, int defaultValue) { try { return value.isEmpty() ? defaultValue : Integer.parseInt(value); } catch (NumberFormatException ex) { return defaultValue; } }
    private boolean booleanValue(String value, boolean defaultValue) { if (value.isEmpty()) return defaultValue; return "true".equalsIgnoreCase(value) || "是".equals(value) || "启用".equals(value) || "1".equals(value); }
}
