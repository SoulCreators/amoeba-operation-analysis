-- 基础配置导入独立于月度业务数据导入，避免映射、指标、权限被误当成月度事实数据。
CREATE TABLE IF NOT EXISTS flow.configuration_import_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    config_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(512),
    checksum VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_configuration_import_type_status
    ON flow.configuration_import_task(config_type, status);
