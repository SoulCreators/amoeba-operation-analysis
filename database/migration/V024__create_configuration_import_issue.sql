-- 配置导入问题独立于月度事实导入问题，避免错误外键和任务类型混用。
CREATE TABLE IF NOT EXISTS flow.configuration_import_issue (
    id BIGSERIAL PRIMARY KEY,
    configuration_task_id BIGINT NOT NULL
        REFERENCES flow.configuration_import_task(id) ON DELETE CASCADE,
    row_no INTEGER,
    field_name VARCHAR(128),
    issue_code VARCHAR(64) NOT NULL,
    issue_message VARCHAR(1000) NOT NULL,
    raw_value VARCHAR(1000)
);

CREATE INDEX IF NOT EXISTS idx_configuration_import_issue_task
    ON flow.configuration_import_issue(configuration_task_id, id);
