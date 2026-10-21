-- V028 计算作业失败自动重试与人工接管最小切片。
-- 本次新增：独立保存重试状态和人工动作审计，不修改 V026/V027，也不写入既有业务数据。
-- 自动重试次数由应用领域策略控制，数据库只约束状态、次数、幂等键和人工审计字段。

-- 本次新增：同一个 job_id/request_key 只允许一条失败处理状态，保证重试编排幂等。
CREATE TABLE IF NOT EXISTS flow.calculation_retry (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    retry_no INTEGER NOT NULL DEFAULT 0,
    last_failed_attempt_no INTEGER,
    next_retry_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calculation_retry_job_id UNIQUE (job_id),
    CONSTRAINT uk_calculation_retry_request_key UNIQUE (request_key),
    CONSTRAINT fk_calculation_retry_job
        FOREIGN KEY (job_id) REFERENCES flow.calculation_job(job_id),
    CONSTRAINT ck_calculation_retry_status CHECK (
        status IN ('ACTIVE', 'PENDING_RETRY', 'PROCESSING', 'SUCCEEDED', 'MANUAL_REQUIRED', 'COMPENSATED')
    ),
    CONSTRAINT ck_calculation_retry_no CHECK (
        retry_no >= 0 AND retry_no <= 3
    ),
    CONSTRAINT ck_calculation_retry_schedule CHECK (
        (status = 'PENDING_RETRY' AND next_retry_at IS NOT NULL)
        OR (status <> 'PENDING_RETRY' AND next_retry_at IS NULL)
    ),
    CONSTRAINT ck_calculation_retry_failure_detail CHECK (
        status NOT IN ('PENDING_RETRY', 'MANUAL_REQUIRED')
        OR (last_failed_attempt_no IS NOT NULL AND last_failed_attempt_no >= 1
            AND last_error IS NOT NULL AND length(btrim(last_error)) > 0)
    )
);

-- 本次新增：Worker 只扫描到期的 PENDING_RETRY，避免重复领取已处理状态。
CREATE INDEX IF NOT EXISTS idx_calculation_retry_due
    ON flow.calculation_retry(status, next_retry_at, job_id)
    WHERE status = 'PENDING_RETRY';

-- 本次新增：人工重试/补偿采用追加审计，保留操作者、原因、原尝试号和处理结果。
CREATE TABLE IF NOT EXISTS flow.calculation_retry_action (
    action_id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(128) NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    operator VARCHAR(128) NOT NULL,
    reason TEXT NOT NULL,
    original_attempt_no INTEGER NOT NULL,
    result VARCHAR(64) NOT NULL,
    result_detail TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calculation_retry_action_job
        FOREIGN KEY (job_id) REFERENCES flow.calculation_job(job_id),
    CONSTRAINT ck_calculation_retry_action_type CHECK (
        action_type IN ('MANUAL_RETRY', 'MANUAL_COMPENSATE')
    ),
    CONSTRAINT ck_calculation_retry_action_attempt_no CHECK (
        original_attempt_no >= 1
    ),
    CONSTRAINT ck_calculation_retry_action_operator CHECK (
        length(btrim(operator)) > 0
    ),
    CONSTRAINT ck_calculation_retry_action_reason CHECK (
        length(btrim(reason)) > 0
    ),
    CONSTRAINT ck_calculation_retry_action_result CHECK (
        length(btrim(result)) > 0
    )
);

-- 本次新增：按作业和时间查询人工处理历史，支持审计和补偿复盘。
CREATE INDEX IF NOT EXISTS idx_calculation_retry_action_job
    ON flow.calculation_retry_action(job_id, occurred_at, action_id);
