package com.sunwoda.evb.finance.analysis.domain.model;

/** 本次新增：IAM 用户主数据；停用通过 enabled 表达，不提供物理删除。 */
public final class IamUser {
    private final String userId;
    private final String employeeNo;
    private final String username;
    private final String displayName;
    private final String email;
    private final String department;
    private final boolean enabled;

    public IamUser(String userId, String displayName, String email, String department, boolean enabled) {
        this(userId, null, null, displayName, email, department, enabled);
    }

    public IamUser(String userId, String employeeNo, String username, String displayName,
                   String email, String department, boolean enabled) {
        this.userId = required(userId, "用户工号");
        this.employeeNo = optional(employeeNo);
        this.username = optional(username);
        this.displayName = required(displayName, "用户姓名");
        this.email = optional(email);
        this.department = optional(department);
        this.enabled = enabled;
    }

    public String getUserId() { return userId; }
    public String getEmployeeNo() { return employeeNo; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public boolean isEnabled() { return enabled; }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
