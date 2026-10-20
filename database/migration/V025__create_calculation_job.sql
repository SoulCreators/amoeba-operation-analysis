-- V025 计算作业最小持久化结构。
-- 本次修改：为 CalculationJob 记录标识、请求键、批次号、生命周期、尝试次数、错误和时间字段，
-- 并用数据库约束锁定状态取值、基础字段不变量、job_id 唯一以及活动状态下 request_key 唯一。
-- 本迁移只创建结构，不写入任何业务数据；用户作用域、重试策略和批次结果事务不在本切片内。
CREATE TABLE IF NOT EXISTS flow.calculation_job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    batch_no VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    attempt_no INTEGER NOT NULL DEFAULT 1,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT uk_calculation_job_job_id UNIQUE (job_id),
    CONSTRAINT ck_calculation_job_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_calculation_job_attempt_no CHECK (attempt_no >= 1),
    CONSTRAINT ck_calculation_job_started_at CHECK (
        started_at IS NULL OR started_at >= created_at
    ),
    CONSTRAINT ck_calculation_job_finished_at CHECK (
        finished_at IS NULL OR started_at IS NULL OR finished_at >= started_at
    ),
    CONSTRAINT ck_calculation_job_failed_error CHECK (
        status <> 'FAILED' OR (last_error IS NOT NULL AND length(btrim(last_error)) > 0)
    )
);

-- 本次修改：活动作业只允许同一 request_key 保留一条，终态作业释放该键以支持后续新的作业。
CREATE UNIQUE INDEX IF NOT EXISTS uk_calculation_job_active_request_key
    ON flow.calculation_job(request_key)
    WHERE status IN ('QUEUED', 'RUNNING');
