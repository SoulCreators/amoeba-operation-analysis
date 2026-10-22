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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies metric definitions and the business-field configuration templates.
 * Permission files are deliberately retained after structural validation and
 * are not written by this adapter until the administrator workflow is connected.
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
        if (task.getType() == ConfigurationImportType.USER_PERMISSION) {
            return validatePermissionRows(inputStream);
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
            Sheet sheet = sheetFor(workbook, ConfigurationImportType.METRIC_DEFINITION);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                String code = first(row, headers, "metriccode", "指标编码", "编码");
                String name = first(row, headers, "metricname", "指标名称", "名称");
                String formula = first(row, headers, "formulaexpression", "计算公式", "公式", "指标公式");
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
                int order = integer(first(row, headers, "displayorder", "展示顺序", "排序"), rowNo * 10);
                boolean enabled = booleanValue(first(row, headers, "enabled", "是否启用", "是否有效"), true);
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
        Map<String, String> seenRows = new HashMap<String, String>();
        Map<String, String> leadersByModel = new HashMap<String, String>();
        Map<String, BigDecimal> inventoryRatios = new HashMap<String, BigDecimal>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = sheetFor(workbook, task.getType());
            Map<String, Integer> headers = headers(sheet.getRow(0));
            boolean genericMapping = has(headers, "matchkey", "匹配键", "组合键", "来源值")
                    && has(headers, "mappedvalue", "映射值", "目标值");
            if (type == MappingType.SALES_PROJECT && !genericMapping
                    && (!has(headers, "出货基地") || !has(headers, "客户项目改"))) {
                issues.add(new ImportIssue(1, !has(headers, "出货基地") ? "出货基地" : "客户项目改",
                        "CONFIG_SCHEMA_BLOCKED",
                        "销售项目映射待补齐出货基地、客户项目改字段，当前模板不能满足四键精确匹配", null));
            }
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                String key;
                String value;
                if (genericMapping) {
                    key = first(row, headers, "matchkey", "匹配键", "组合键", "来源值");
                    value = first(row, headers, "mappedvalue", "映射值", "目标值");
                } else {
                    MappingFields fields = mappingFields(task.getType(), headers);
                    validateRequiredValues(row, headers, fields.requiredHeaders, rowNo, issues);
                    key = fields.key(row, headers);
                    value = fields.value(row, headers);
                }
                if (key.isEmpty() || value.isEmpty()) {
                    issues.add(new ImportIssue(rowNo + 1, null, "FIELD_MISSING",
                            genericMapping ? "映射配置必须包含匹配键和映射值"
                                    : "映射配置的业务键和输出字段不能为空", null));
                    continue;
                }
                boolean enabled = booleanValue(first(row, headers, "enabled", "是否启用", "是否有效"), true);
                if (enabled && type == MappingType.SALES_PROJECT) {
                    String model = first(row, headers, "电芯型号");
                    // Only the shared leader field is unambiguous for non-financial
                    // ownership. Product-line and BU leaders are separate dimensions.
                    String leader = has(headers, "产品线巴长工号", "事业部巴长工号")
                            ? "" : first(row, headers, "巴长工号");
                    if (!model.isEmpty() && !leader.isEmpty()) {
                        String previousLeader = leadersByModel.putIfAbsent(normalizeBusinessValue(model), leader);
                        if (previousLeader != null && !previousLeader.equals(leader)) {
                            issues.add(new ImportIssue(rowNo + 1, "巴长工号", "MAPPING_CONFLICT",
                                    "同一电芯型号只能对应一个有效巴长工号: " + model, leader));
                        }
                    }
                }
                if (enabled && type == MappingType.INVENTORY) {
                    String material = first(row, headers, "物料编码");
                    String ratioText = first(row, headers, "分摊比例");
                    if (!material.isEmpty() && !ratioText.isEmpty()) {
                        try {
                            BigDecimal ratio = new BigDecimal(ratioText.replace("%", "").trim());
                            if (ratioText.contains("%")) ratio = ratio.movePointLeft(2);
                            if (ratio.signum() < 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
                                throw new NumberFormatException("比例超出0到1范围");
                            }
                            String ratioKey = normalizeBusinessValue(material);
                            inventoryRatios.put(ratioKey,
                                    inventoryRatios.getOrDefault(ratioKey, BigDecimal.ZERO).add(ratio));
                        } catch (NumberFormatException ex) {
                            issues.add(new ImportIssue(rowNo + 1, "分摊比例", "INVALID_RATIO",
                                    "库存分摊比例必须是0到1之间的十进制比例", ratioText));
                        }
                    }
                }
                String rowSignature = key + "\u0000" + value + "\u0000" + enabled;
                String previousSignature = seenRows.get(key);
                if (previousSignature != null) {
                    if (previousSignature.equals(rowSignature)) continue;
                    issues.add(new ImportIssue(rowNo + 1, "匹配键", "DUPLICATE_KEY",
                            "同一文件内匹配键对应多个不同映射结果: " + key, key));
                    continue;
                }
                seenRows.put(key, rowSignature);
                rows.add(new MappingRow(key, value, enabled));
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "APPLY_FAILED", "映射配置应用失败: " + ex.getMessage(), null));
        }
        if (type == MappingType.INVENTORY) {
            for (Map.Entry<String, BigDecimal> ratio : inventoryRatios.entrySet()) {
                if (ratio.getValue().compareTo(BigDecimal.ONE) != 0) {
                    issues.add(new ImportIssue(null, "分摊比例", "ALLOCATION_RATIO_NOT_ONE",
                            "同一物料的有效分摊比例合计必须为1；当前物料 " + ratio.getKey()
                                    + " 合计为 " + ratio.getValue().toPlainString(),
                            ratio.getValue().toPlainString()));
                }
            }
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

    /**
     * The real 21-26 templates have no generic matchKey/mappedValue columns.
     * This adapter keeps the existing MappingEntryRepository contract by
     * composing each template's documented business key and output columns in
     * their template order, separated by the same pipe convention used by the
     * mapping API. The individual fields remain recoverable by their stable
     * order and are not replaced by a front-end-only synthetic field.
     */
    private MappingFields mappingFields(ConfigurationImportType type, Map<String, Integer> headers) {
        switch (type) {
            case SALES_PROJECT_MAPPING:
                if (has(headers, "产品线巴长", "事业部巴长")) {
                    return new MappingFields(
                            new String[]{"出货基地", "客户简称", "客户项目改", "电芯型号"},
                            new String[]{"产品线巴长", "产品线巴长工号", "事业部巴长", "事业部巴长工号", "备注"},
                            new String[]{"出货基地", "客户简称", "客户项目改", "电芯型号", "产品线", "事业部", "是否有效"});
                }
                return new MappingFields(
                        new String[]{"出货基地", "客户简称", "客户项目改", "电芯型号"},
                        new String[]{"产品线", "事业部", "B品标识", "备注", "巴长", "巴长工号"},
                        new String[]{"出货基地", "客户简称", "客户项目改", "电芯型号", "产品线", "事业部", "是否有效"});
            case INVENTORY_MAPPING:
                if (has(headers, "物料描述")) {
                    return new MappingFields(
                        new String[]{"物料编码", "物料描述", "电芯型号", "产品类型", "产品线", "事业部"},
                        new String[]{"分摊比例", "备注"},
                        new String[]{"物料编码", "物料描述", "产品类型", "产品线", "事业部", "分摊比例", "是否有效"});
                }
                return new MappingFields(
                        new String[]{"物料编码"},
                        new String[]{"电芯型号", "产品类型", "产品线", "事业部", "分摊比例", "备注"},
                        new String[]{"物料编码", "电芯型号", "产品线", "事业部", "分摊比例", "是否有效"});
            case EXPENSE_CATEGORY:
                if (has(headers, "公司段", "BU段", "部门段")) {
                    return new MappingFields(
                            new String[]{"公司段", "BU段", "部门段", "部门名称"},
                            new String[]{"基地", "基地重分类", "部门性质", "备注"},
                            new String[]{"公司段", "BU段", "部门段", "部门名称", "基地", "基地重分类", "部门性质", "是否有效"});
                }
                return new MappingFields(
                        new String[]{"三级费用编码", "三级费用名称"},
                        new String[]{"费用分类", "基地", "产品线", "事业部", "备注"},
                        new String[]{"三级费用名称", "费用分类", "是否有效"}) {
                    @Override
                    String key(Row row, Map<String, Integer> headers) {
                        String code = first(row, headers, "三级费用编码");
                        return code.isEmpty() ? first(row, headers, "三级费用名称") : code;
                    }
                };
            case EXPENSE_ALLOCATION:
                return new MappingFields(
                        new String[]{"期间", "费用项目", "来源基地", "目标基地"},
                        new String[]{"分摊比例", "备注"},
                        new String[]{"期间", "费用项目", "来源基地", "目标基地", "分摊比例", "是否有效"});
            case FX_RATE:
                return new MappingFields(
                        new String[]{"期间", "基地", "币种"},
                        new String[]{"汇率", "换算方向", "备注"},
                        new String[]{"期间", "基地", "币种", "汇率", "换算方向", "是否有效"});
            case BASE_GRAIN:
                return new MappingFields(
                        new String[]{"基地", "电芯型号"},
                        new String[]{"分析粒度", "备注"},
                        new String[]{"基地", "电芯型号", "分析粒度", "是否有效"});
            default:
                throw new IllegalArgumentException("不支持按业务字段应用的配置类型: " + type);
        }
    }

    private ConfigurationApplyResult validatePermissionRows(InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = sheetFor(workbook, ConfigurationImportType.USER_PERMISSION);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            String[] required = new String[]{"姓名", "工号", "用户角色", "数据视角", "可查看范围",
                    "明细查看权限", "数据导出权限", "配置维护范围", "是否有效"};
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                validateRequiredValues(row, headers, required, rowNo, issues);
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "APPLY_FAILED",
                    "用户权限配置结构检查失败: " + ex.getMessage(), null));
        }
        return new ConfigurationApplyResult(0, issues);
    }

    private void validateRequiredValues(Row row, Map<String, Integer> headers,
                                        String[] requiredHeaders, int rowNo,
                                        List<ImportIssue> issues) {
        for (String header : requiredHeaders) {
            String value = first(row, headers, header);
            // Finance's department master contains legacy rows with a blank
            // effective flag. The adapter treats those rows as enabled, which
            // is also the existing default used by booleanValue(...).
            if (value.isEmpty() && "是否有效".equals(header)) continue;
            if (value.isEmpty()) {
                issues.add(new ImportIssue(rowNo + 1, header, "FIELD_MISSING",
                        "必填字段不能为空: " + header, null));
            }
        }
    }

    private boolean has(Map<String, Integer> headers, String... aliases) {
        for (String alias : aliases) {
            if (headers.containsKey(normalize(alias))) return true;
        }
        return false;
    }

    private class MappingFields {
        private final String[] keyHeaders;
        private final String[] valueHeaders;
        private final String[] requiredHeaders;

        private MappingFields(String[] keyHeaders, String[] valueHeaders, String[] requiredHeaders) {
            this.keyHeaders = keyHeaders;
            this.valueHeaders = valueHeaders;
            this.requiredHeaders = requiredHeaders;
        }

        String key(Row row, Map<String, Integer> headers) {
            return join(row, headers, keyHeaders);
        }

        String value(Row row, Map<String, Integer> headers) {
            return join(row, headers, valueHeaders);
        }

        private String join(Row row, Map<String, Integer> headers, String[] fields) {
            StringBuilder result = new StringBuilder();
            for (String field : fields) {
                if (result.length() > 0) result.append('|');
                result.append(first(row, headers, field));
            }
            return result.toString();
        }
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
    private String normalizeBusinessValue(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
    private String defaultIfEmpty(String value, String defaultValue) { return value.isEmpty() ? defaultValue : value; }
    private int integer(String value, int defaultValue) { try { return value.isEmpty() ? defaultValue : Integer.parseInt(value); } catch (NumberFormatException ex) { return defaultValue; } }
    private boolean booleanValue(String value, boolean defaultValue) { if (value.isEmpty()) return defaultValue; return "true".equalsIgnoreCase(value) || "是".equals(value) || "启用".equals(value) || "1".equals(value); }

    private Sheet sheetFor(Workbook workbook, ConfigurationImportType type) {
        String expected = expectedSheetName(type);
        if (expected != null) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (expected.equals(sheet.getSheetName())) return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    private String expectedSheetName(ConfigurationImportType type) {
        switch (type) {
            case SALES_PROJECT_MAPPING: return "21_销售项目映射";
            case INVENTORY_MAPPING: return "22_库存档案映射";
            case EXPENSE_CATEGORY: return "23_费用分类映射";
            case USER_PERMISSION: return "28_用户权限";
            default: return null;
        }
    }
}
