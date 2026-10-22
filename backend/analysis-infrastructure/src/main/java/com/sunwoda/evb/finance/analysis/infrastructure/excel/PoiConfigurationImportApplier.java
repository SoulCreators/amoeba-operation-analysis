package com.sunwoda.evb.finance.analysis.infrastructure.excel;

import com.sunwoda.evb.finance.analysis.application.port.ConfigurationImportApplier;
import com.sunwoda.evb.finance.analysis.application.service.IamAdminService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationApplyResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.MetricDefinitionRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Applies metric definitions and the business-field configuration templates.
 * In the production Spring wiring, permission files are atomically applied through
 * the IAM administrator service after the entire workbook passes validation.
 */
@Repository
public class PoiConfigurationImportApplier implements ConfigurationImportApplier {
    private final MetricDefinitionRepository metricRepository;
    private final MappingEntryRepository mappingRepository;
    private final IamAdminService iamAdminService;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public PoiConfigurationImportApplier(MetricDefinitionRepository metricRepository,
                                         MappingEntryRepository mappingRepository) {
        this(metricRepository, mappingRepository, null);
    }

    /** 本次修改：生产环境接入 IAM 管理服务，使权限模板在 ADMIN 审批边界内原子应用。 */
    @Autowired
    public PoiConfigurationImportApplier(MetricDefinitionRepository metricRepository,
                                         MappingEntryRepository mappingRepository,
                                         IamAdminService iamAdminService) {
        this.metricRepository = metricRepository;
        this.mappingRepository = mappingRepository;
        this.iamAdminService = iamAdminService;
    }

    @Override
    public ConfigurationApplyResult apply(ConfigurationImportTask task, InputStream inputStream) {
        if (task.getType() == ConfigurationImportType.METRIC_DEFINITION) {
            return applyMetrics(task, inputStream);
        }
        if (task.getType() == ConfigurationImportType.USER_PERMISSION) {
            return applyPermissions(task, inputStream);
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
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = sheetFor(workbook, task.getType());
            Map<String, Integer> headers = headers(sheet.getRow(0));
            boolean genericMapping = has(headers, "matchkey", "匹配键", "组合键", "来源值")
                    && has(headers, "mappedvalue", "映射值", "目标值");
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
                            new String[]{"客户简称", "客户项目", "电芯型号", "产品线", "事业部", "B品标识"},
                            new String[]{"产品线巴长", "产品线巴长工号", "事业部巴长", "事业部巴长工号", "备注"},
                            new String[]{"客户简称", "客户项目", "产品线", "事业部", "是否有效"});
                }
                return new MappingFields(
                        new String[]{"客户简称", "客户项目", "电芯型号"},
                        new String[]{"产品线", "事业部", "B品标识", "备注", "巴长", "巴长工号"},
                        new String[]{"客户简称", "产品线", "事业部", "是否有效"});
            case INVENTORY_MAPPING:
                if (has(headers, "物料描述")) {
                    return new MappingFields(
                            new String[]{"物料编码", "物料描述", "电芯型号", "产品类型", "产品线", "事业部"},
                            new String[]{"分摊比例", "备注"},
                            new String[]{"物料编码", "物料描述", "产品类型", "产品线", "事业部", "是否有效"});
                }
                return new MappingFields(
                        new String[]{"物料编码"},
                        new String[]{"电芯型号", "产品类型", "产品线", "事业部", "分摊比例", "备注"},
                        new String[]{"物料编码", "电芯型号", "产品线", "事业部", "是否有效"});
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

    private ConfigurationApplyResult applyPermissions(ConfigurationImportTask task, InputStream inputStream) {
        List<ImportIssue> issues = new ArrayList<ImportIssue>();
        Map<String, PermissionUser> users = new LinkedHashMap<String, PermissionUser>();
        Map<String, String> roleNames = new LinkedHashMap<String, String>();
        int rowCount = 0;
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = sheetFor(workbook, ConfigurationImportType.USER_PERMISSION);
            Map<String, Integer> headers = headers(sheet.getRow(0));
            String[] required = new String[]{"姓名", "工号", "用户角色", "数据视角", "可查看范围",
                    "明细查看权限", "数据导出权限", "配置维护范围", "是否有效"};
            for (int rowNo = 1; rowNo <= sheet.getLastRowNum(); rowNo++) {
                Row row = sheet.getRow(rowNo);
                if (blank(row)) continue;
                rowCount++;
                validateRequiredValues(row, headers, required, rowNo, issues);
                String employeeNo = first(row, headers, "工号").trim();
                String displayName = first(row, headers, "姓名").trim();
                String roleName = first(row, headers, "用户角色").trim();
                String roleCode = roleCode(roleName);
                AnalysisPerspective perspective = perspective(first(row, headers, "数据视角"), rowNo, issues);
                List<String> scopeCodes = scopeCodes(first(row, headers, "可查看范围"));
                Boolean detail = permissionBoolean(first(row, headers, "明细查看权限"));
                Boolean export = permissionBoolean(first(row, headers, "数据导出权限"));
                Boolean enabled = permissionBoolean(first(row, headers, "是否有效"));
                String configRange = first(row, headers, "配置维护范围");
                if (roleCode == null) issues.add(new ImportIssue(rowNo + 1, "用户角色", "ROLE_INVALID",
                        "用户角色必须使用已知角色名称或稳定角色编码", roleName));
                if (scopeCodes.isEmpty()) issues.add(new ImportIssue(rowNo + 1, "可查看范围", "SCOPE_INVALID",
                        "可查看范围至少包含一个范围编码", null));
                if (detail == null) issues.add(booleanIssue(rowNo, "明细查看权限"));
                if (export == null) issues.add(booleanIssue(rowNo, "数据导出权限"));
                if (enabled == null) issues.add(booleanIssue(rowNo, "是否有效"));
                if (employeeNo.isEmpty() || displayName.isEmpty() || roleCode == null || perspective == null
                        || scopeCodes.isEmpty() || detail == null || export == null || enabled == null) continue;
                roleNames.put(roleCode, roleName);
                PermissionUser user = users.get(employeeNo);
                if (user == null) {
                    user = new PermissionUser(employeeNo, displayName, enabled.booleanValue());
                    users.put(employeeNo, user);
                } else if (!user.displayName.equals(displayName) || user.enabled != enabled.booleanValue()) {
                    issues.add(new ImportIssue(rowNo + 1, "工号", "USER_CONFLICT",
                            "同一工号的姓名或是否有效不一致: " + employeeNo, employeeNo));
                    continue;
                }
                user.roles.add(roleCode);
                if ("ADMIN".equals(roleCode)) continue;
                for (String scopeCode : scopeCodes) {
                    UserScope grant = new UserScope(employeeNo, roleCode, perspective, scopeCode,
                            detail.booleanValue(), export.booleanValue(), configAllowed(configRange, scopeCode), true);
                    String key = roleCode + "|" + perspective.getCode() + "|" + scopeCode;
                    UserScope previous = user.grants.get(key);
                    if (previous != null && !sameCapabilities(previous, grant)) {
                        issues.add(new ImportIssue(rowNo + 1, "可查看范围", "DUPLICATE_KEY",
                                "同一用户、角色、视角和范围存在冲突授权: " + key, scopeCode));
                    } else {
                        user.grants.put(key, grant);
                    }
                }
            }
        } catch (Exception ex) {
            issues.add(new ImportIssue(null, null, "APPLY_FAILED",
                    "用户权限配置解析失败: " + ex.getMessage(), null));
        }
        // 本次修改：旧切片测试的双参数构造器继续执行结构检查；Spring 生产接线始终注入 IAM 服务并实际应用。
        if (iamAdminService == null) return new ConfigurationApplyResult(0, issues);
        if (!issues.isEmpty()) return new ConfigurationApplyResult(0, issues);

        String operator = task.getCreatedBy();
        Map<String, IamRole> existingRoles = new LinkedHashMap<String, IamRole>();
        for (IamRole role : iamAdminService.listRoles(operator, true)) existingRoles.put(role.getRoleCode(), role);
        for (Map.Entry<String, String> role : roleNames.entrySet()) {
            IamRole existing = existingRoles.get(role.getKey());
            if (existing != null && !existing.isEnabled()) {
                issues.add(new ImportIssue(null, "用户角色", "ROLE_DISABLED",
                        "角色已停用，不能通过权限模板重新授权: " + role.getKey(), role.getKey()));
            }
        }
        if (!issues.isEmpty()) return new ConfigurationApplyResult(0, issues);
        for (Map.Entry<String, String> role : roleNames.entrySet()) {
            if (!existingRoles.containsKey(role.getKey())) {
                iamAdminService.createRole(operator, new IamRole(role.getKey(), role.getValue(),
                        "由用户权限初始化模板创建", "ADMIN".equals(role.getKey()), true));
            }
        }

        Map<String, IamUser> existingUsers = new LinkedHashMap<String, IamUser>();
        for (IamUser user : iamAdminService.listUsers(operator, true)) existingUsers.put(user.getUserId(), user);
        for (PermissionUser row : users.values()) {
            IamUser existing = existingUsers.get(row.employeeNo);
            IamUser value = new IamUser(row.employeeNo, row.employeeNo,
                    existing == null ? row.employeeNo : existing.getUsername(), row.displayName,
                    existing == null ? null : existing.getEmail(), existing == null ? null : existing.getDepartment(),
                    row.enabled);
            List<String> roles = new ArrayList<String>(row.roles);
            if (existing == null) {
                iamAdminService.createUser(operator, value, roles,
                        java.util.Collections.<com.sunwoda.evb.finance.analysis.domain.model.DataScopeSelection>emptyList());
            } else {
                iamAdminService.updateUser(operator, value, roles, null);
            }
            iamAdminService.replaceUserAuthorization(operator, row.employeeNo, roles,
                    new ArrayList<UserScope>(row.grants.values()));
        }
        return new ConfigurationApplyResult(rowCount, issues);
    }

    private String roleCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.matches("[A-Za-z][A-Za-z0-9_-]{1,63}")) return normalized.toUpperCase(Locale.ROOT);
        if ("系统管理员".equals(normalized) || "管理员".equals(normalized) || "财经IT".equalsIgnoreCase(normalized)) return "ADMIN";
        if ("总部财经".equals(normalized)) return "HQ_FINANCE";
        if ("基地财经".equals(normalized)) return "BASE_FINANCE";
        if ("产品线财经".equals(normalized)) return "PRODUCT_LINE_FINANCE";
        if ("事业部财经".equals(normalized)) return "BUSINESS_UNIT_FINANCE";
        return null;
    }

    private AnalysisPerspective perspective(String value, int rowNo, List<ImportIssue> issues) {
        String normalized = value == null ? "" : value.trim();
        if ("基地".equals(normalized)) return AnalysisPerspective.BASE;
        if ("产品线".equals(normalized)) return AnalysisPerspective.PRODUCT_LINE;
        if ("事业部".equals(normalized)) return AnalysisPerspective.BUSINESS_UNIT;
        try { return AnalysisPerspective.fromCode(normalized); }
        catch (IllegalArgumentException ex) {
            issues.add(new ImportIssue(rowNo + 1, "数据视角", "PERSPECTIVE_INVALID", ex.getMessage(), normalized));
            return null;
        }
    }

    private List<String> scopeCodes(String value) {
        Set<String> result = new LinkedHashSet<String>();
        if (value != null) for (String code : value.split("[,，;；\\s]+")) {
            if (!code.trim().isEmpty()) result.add(code.trim().toUpperCase(Locale.ROOT));
        }
        return new ArrayList<String>(result);
    }

    private Boolean permissionBoolean(String value) {
        String normalized = value == null ? "" : value.trim();
        if ("是".equals(normalized) || "有".equals(normalized) || "允许".equals(normalized)
                || "启用".equals(normalized) || "1".equals(normalized) || "true".equalsIgnoreCase(normalized)) return Boolean.TRUE;
        if ("否".equals(normalized) || "无".equals(normalized) || "不允许".equals(normalized)
                || "停用".equals(normalized) || "0".equals(normalized) || "false".equalsIgnoreCase(normalized)) return Boolean.FALSE;
        return null;
    }

    private boolean configAllowed(String value, String scopeCode) {
        Boolean direct = permissionBoolean(value);
        if (direct != null) return direct.booleanValue();
        List<String> ranges = scopeCodes(value);
        return ranges.contains("ALL") || ranges.contains(scopeCode);
    }

    private ImportIssue booleanIssue(int rowNo, String field) {
        return new ImportIssue(rowNo + 1, field, "BOOLEAN_INVALID", field + "仅支持是/否、允许/不允许、1/0或true/false", null);
    }

    private boolean sameCapabilities(UserScope left, UserScope right) {
        return left.isCanViewDetail() == right.isCanViewDetail()
                && left.isCanExport() == right.isCanExport()
                && left.isCanEditConfig() == right.isCanEditConfig();
    }

    private static final class PermissionUser {
        private final String employeeNo;
        private final String displayName;
        private final boolean enabled;
        private final Set<String> roles = new LinkedHashSet<String>();
        private final Map<String, UserScope> grants = new LinkedHashMap<String, UserScope>();
        private PermissionUser(String employeeNo, String displayName, boolean enabled) {
            this.employeeNo = employeeNo; this.displayName = displayName; this.enabled = enabled;
        }
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
