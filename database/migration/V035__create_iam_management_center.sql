-- 本次新增：IAM 管理中心主数据。用户、角色、菜单只允许停用，不提供物理删除语义。
CREATE TABLE IF NOT EXISTS iam.platform_user (
    user_id VARCHAR(64) PRIMARY KEY,
    employee_no VARCHAR(64),
    username VARCHAR(128),
    display_name VARCHAR(128) NOT NULL,
    email VARCHAR(255),
    department VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS iam.role (
    role_code VARCHAR(64) PRIMARY KEY,
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS iam.menu (
    menu_code VARCHAR(64) PRIMARY KEY,
    menu_name VARCHAR(128) NOT NULL,
    parent_code VARCHAR(64),
    route_path VARCHAR(255),
    permission_code VARCHAR(128),
    display_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS iam.user_role (
    user_id VARCHAR(64) NOT NULL REFERENCES iam.platform_user(user_id),
    role_code VARCHAR(64) NOT NULL REFERENCES iam.role(role_code),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_code)
);

CREATE TABLE IF NOT EXISTS iam.role_menu (
    role_code VARCHAR(64) NOT NULL REFERENCES iam.role(role_code),
    menu_code VARCHAR(64) NOT NULL REFERENCES iam.menu(menu_code),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, menu_code)
);

CREATE INDEX IF NOT EXISTS idx_platform_user_enabled ON iam.platform_user(enabled, user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_user_employee_no
    ON iam.platform_user(employee_no) WHERE employee_no IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_platform_user_username
    ON iam.platform_user(username) WHERE username IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_role_enabled ON iam.role(enabled, role_code);
CREATE INDEX IF NOT EXISTS idx_menu_enabled ON iam.menu(enabled, display_order, menu_code);
CREATE INDEX IF NOT EXISTS idx_user_role_lookup ON iam.user_role(user_id, enabled, role_code);
CREATE INDEX IF NOT EXISTS idx_role_menu_lookup ON iam.role_menu(role_code, enabled, menu_code);

-- 本次新增：先登记内置 ADMIN 角色；仅登记角色不会授予任何用户管理员身份。
INSERT INTO iam.role(role_code, role_name, description, system_role, enabled, created_by, updated_by)
VALUES ('ADMIN', '系统管理员', '管理用户、角色、菜单与数据权限；不包含经营计算、确认或发布权限', TRUE, TRUE, 'MIGRATION', 'MIGRATION')
ON CONFLICT (role_code) DO NOTHING;

-- 本次兼容：把历史 user_scope 中已经存在的用户、角色和关系回填到新主数据表。
INSERT INTO iam.platform_user(user_id, employee_no, username, display_name, enabled, created_by, updated_by)
SELECT DISTINCT user_id, user_id, user_id, user_id, TRUE, 'MIGRATION', 'MIGRATION'
FROM iam.user_scope
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO iam.role(role_code, role_name, system_role, enabled, created_by, updated_by)
SELECT DISTINCT role_code, role_code, UPPER(role_code) = 'ADMIN', TRUE, 'MIGRATION', 'MIGRATION'
FROM iam.user_scope
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO iam.user_role(user_id, role_code, enabled, created_by, updated_by)
SELECT DISTINCT user_id, role_code, TRUE, 'MIGRATION', 'MIGRATION'
FROM iam.user_scope
ON CONFLICT (user_id, role_code) DO UPDATE SET
    enabled = TRUE,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;
