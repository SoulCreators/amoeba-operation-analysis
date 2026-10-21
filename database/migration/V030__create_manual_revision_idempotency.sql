-- V030 跨实例人工修订持久幂等切片。
-- 本次新增：以 user_id + request_key 作为数据库唯一业务键，保存请求载荷、领取状态和目标版本。
-- 领取记录与人工修订结果必须在同一事务中提交；失败回滚后，其他实例可以重新领取同一请求。

CREATE TABLE IF NOT EXISTS flow.manual_revision_idempotency (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    request_key VARCHAR(255) NOT NULL,
    source_calculation_version_id VARCHAR(128) NOT NULL
        REFERENCES calc.calculation_version(calculation_version_id),
    revision_reason TEXT NOT NULL,
    request_payload_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    target_calculation_version_id VARCHAR(128)
        REFERENCES calc.calculation_version(calculation_version_id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_manual_revision_idempotency_user_request
        UNIQUE (user_id, request_key),
    CONSTRAINT ck_manual_revision_idempotency_payload_object CHECK (
        jsonb_typeof(request_payload_json) = 'object'
    ),
    CONSTRAINT ck_manual_revision_idempotency_status CHECK (
        status IN ('IN_PROGRESS', 'COMPLETED')
    ),
    CONSTRAINT ck_manual_revision_idempotency_completion CHECK (
        (status = 'IN_PROGRESS' AND target_calculation_version_id IS NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND target_calculation_version_id IS NOT NULL
            AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_manual_revision_idempotency_user CHECK (
        length(btrim(user_id)) > 0
    ),
    CONSTRAINT ck_manual_revision_idempotency_request_key CHECK (
        length(btrim(request_key)) > 0
    ),
    CONSTRAINT ck_manual_revision_idempotency_reason CHECK (
        length(btrim(revision_reason)) > 0
    )
);

-- 本次新增：按状态和创建时间提供运维审计入口，不参与领取裁决；唯一键仍由上面的约束保证。
CREATE INDEX IF NOT EXISTS idx_manual_revision_idempotency_status
    ON flow.manual_revision_idempotency(status, created_at, id);
