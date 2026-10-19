-- 导出任务元数据；文件字节由独立文件存储适配器负责，当前仓储不写入 bytea。
CREATE TABLE IF NOT EXISTS flow.export_task (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_export_task_user_request UNIQUE (user_id, request_key),
    CONSTRAINT ck_export_task_status CHECK (status IN (
        'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'EXPIRED'
    )),
    CONSTRAINT ck_export_task_expiry CHECK (expires_at >= created_at),
    CONSTRAINT ck_export_task_failed_message CHECK (
        status <> 'FAILED' OR (error_message IS NOT NULL AND length(trim(error_message)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_export_task_user_status
    ON flow.export_task(user_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_export_task_expiry
    ON flow.export_task(expires_at)
    WHERE status <> 'EXPIRED';
