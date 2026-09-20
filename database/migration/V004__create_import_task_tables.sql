-- 月度业务数据导入任务：只承载收入成本、费用、减值、闲置等业务数据；映射和指标配置走独立配置入口。
CREATE TABLE IF NOT EXISTS flow.import_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL UNIQUE,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    dataset_code VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(512),
    checksum VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    issue_count INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_import_task_batch_dataset
    ON flow.import_task(batch_id, dataset_code);

CREATE INDEX IF NOT EXISTS idx_import_task_batch_status
    ON flow.import_task(batch_id, status);

CREATE TABLE IF NOT EXISTS flow.import_issue (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES flow.import_task(id),
    row_no INTEGER,
    field_name VARCHAR(128),
    issue_code VARCHAR(64) NOT NULL,
    issue_message VARCHAR(1000) NOT NULL,
    raw_value VARCHAR(1000),
    ignored_flag BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_import_issue_task
    ON flow.import_issue(task_id, ignored_flag);
