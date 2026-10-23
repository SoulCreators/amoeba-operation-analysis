-- 异常工作台：对导入异常进行阻断/警告分级并跟踪处理状态（未处理/已忽略）。
-- 阻断项不可忽略、忽略警告记录人/时间/原因、复检重置忽略，均由应用层强制；本迁移只落结构与约束，不写业务数据。
CREATE TABLE IF NOT EXISTS flow.exception_work_item (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(64) NOT NULL,
    object_code VARCHAR(64) NOT NULL,
    issue_code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'BLOCKING',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    row_no INTEGER,
    field_name VARCHAR(128),
    issue_message VARCHAR(1000) NOT NULL,
    raw_value VARCHAR(1000),
    ignored_by VARCHAR(64),
    ignored_at TIMESTAMP,
    ignore_reason VARCHAR(1000),
    CONSTRAINT ck_exception_work_item_severity
        CHECK (severity IN ('BLOCKING', 'WARNING')),
    CONSTRAINT ck_exception_work_item_status
        CHECK (status IN ('OPEN', 'IGNORED'))
);

CREATE INDEX IF NOT EXISTS idx_exception_work_item_task
    ON flow.exception_work_item(task_no, status);
