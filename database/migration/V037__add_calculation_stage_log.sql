-- V037 计算作业阶段日志与心跳最小切片。
-- 本次修改：为计算作业补充当前阶段、心跳、错误码、追踪号、累计处理行数与异常数，
-- 并新增独立阶段日志表记录每阶段开始/结束时间、行数、金额控制数、异常数、错误码与追踪号。
-- 本迁移只创建结构与约束，不写入任何业务数据；阶段推进由应用领域策略控制。
-- 金额控制数使用 NUMERIC 精确存储，禁止二进制浮点。

ALTER TABLE flow.calculation_job
    ADD COLUMN IF NOT EXISTS stage VARCHAR(32),
    ADD COLUMN IF NOT EXISTS heartbeat_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS processed_rows BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS issue_count INTEGER NOT NULL DEFAULT 0;

-- 本次修改：阶段取值锁定，避免持久化未知阶段；阶段为 NULL 表示尚未进入任何阶段。
ALTER TABLE flow.calculation_job
    DROP CONSTRAINT IF EXISTS ck_calculation_job_stage;
ALTER TABLE flow.calculation_job
    ADD CONSTRAINT ck_calculation_job_stage CHECK (
        stage IS NULL OR stage IN (
            'NORMALIZE', 'MAP', 'ALLOCATE', 'PNL', 'PVM', 'RECONCILE', 'MATERIALIZE'
        )
    );

-- 本次修改：累计处理行数与异常数不可为负。
ALTER TABLE flow.calculation_job
    DROP CONSTRAINT IF EXISTS ck_calculation_job_processed_rows;
ALTER TABLE flow.calculation_job
    ADD CONSTRAINT ck_calculation_job_processed_rows CHECK (processed_rows >= 0);
ALTER TABLE flow.calculation_job
    DROP CONSTRAINT IF EXISTS ck_calculation_job_issue_count;
ALTER TABLE flow.calculation_job
    ADD CONSTRAINT ck_calculation_job_issue_count CHECK (issue_count >= 0);

-- 本次修改：独立阶段日志，按 (job_id, stage) 幂等；外键沿用 V028 对 calculation_job(job_id) 的引用约定。
CREATE TABLE IF NOT EXISTS flow.calculation_stage_log (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    control_amount NUMERIC(30, 6),
    issue_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    trace_id VARCHAR(128),
    message TEXT,
    CONSTRAINT uk_calculation_stage_log_job_stage UNIQUE (job_id, stage),
    CONSTRAINT fk_calculation_stage_log_job
        FOREIGN KEY (job_id) REFERENCES flow.calculation_job(job_id),
    CONSTRAINT ck_calculation_stage_log_stage CHECK (
        stage IN ('NORMALIZE', 'MAP', 'ALLOCATE', 'PNL', 'PVM', 'RECONCILE', 'MATERIALIZE')
    ),
    CONSTRAINT ck_calculation_stage_log_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_calculation_stage_log_processed_rows CHECK (processed_rows >= 0),
    CONSTRAINT ck_calculation_stage_log_issue_count CHECK (issue_count >= 0),
    CONSTRAINT ck_calculation_stage_log_finished_at CHECK (
        finished_at IS NULL OR finished_at >= started_at
    ),
    CONSTRAINT ck_calculation_stage_log_failed_error CHECK (
        status <> 'FAILED' OR (error_code IS NOT NULL AND length(btrim(error_code)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_calculation_stage_log_job_id
    ON flow.calculation_stage_log(job_id, id);
