-- V026 计算结果版本、计算输入快照和生命周期 Outbox 数据底座。
-- 本次修改：为后续统一事务协调提供不可变结果版本、输入快照和事件待发送记录。
-- 本迁移不接入 application/infrastructure/worker，不改变权限，也不写入既有业务数据。

-- 本次修改：计算快照保存实际使用的配置/来源集合摘要，创建后由应用层按不可变对象使用。
CREATE TABLE IF NOT EXISTS calc.calculation_snapshot (
    snapshot_id VARCHAR(128) PRIMARY KEY,
    snapshot_type VARCHAR(64) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    content_json JSONB NOT NULL,
    source_version_set JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash VARCHAR(128) NOT NULL,
    source_version_hash VARCHAR(128),
    rule_version VARCHAR(64),
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_calculation_snapshot_content_object CHECK (
        jsonb_typeof(content_json) = 'object'
    ),
    CONSTRAINT ck_calculation_snapshot_source_versions_array CHECK (
        jsonb_typeof(source_version_set) = 'array'
    ),
    CONSTRAINT ck_calculation_snapshot_content_hash CHECK (
        length(btrim(content_hash)) > 0
    )
);

CREATE INDEX IF NOT EXISTS idx_calculation_snapshot_hash
    ON calc.calculation_snapshot(content_hash, snapshot_type);

-- 本次修改：结果版本头独立于结果范围明细，保存任务、快照、输入指纹和替代关系。
CREATE TABLE IF NOT EXISTS calc.calculation_version (
    calculation_version_id VARCHAR(128) PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    batch_no VARCHAR(128) NOT NULL,
    perspective VARCHAR(32) NOT NULL,
    version_no VARCHAR(64) NOT NULL,
    job_id VARCHAR(128) NOT NULL REFERENCES flow.calculation_job(job_id),
    snapshot_id VARCHAR(128) NOT NULL REFERENCES calc.calculation_snapshot(snapshot_id),
    source_version_hash VARCHAR(128) NOT NULL,
    config_hash VARCHAR(128) NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    current_flag BOOLEAN NOT NULL DEFAULT TRUE,
    supersedes_version_id VARCHAR(128),
    trace_id VARCHAR(128),
    calculated_by VARCHAR(128) NOT NULL,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_by VARCHAR(128),
    confirmed_at TIMESTAMP,
    published_by VARCHAR(128),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_calculation_version_batch_version UNIQUE (batch_id, version_no),
    CONSTRAINT fk_calculation_version_supersedes
        FOREIGN KEY (supersedes_version_id)
        REFERENCES calc.calculation_version(calculation_version_id),
    CONSTRAINT ck_calculation_version_status CHECK (
        status IN ('CALCULATED', 'CONFIRMED', 'PUBLISHED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_calculation_version_batch_no CHECK (
        length(btrim(batch_no)) > 0
    ),
    CONSTRAINT ck_calculation_version_self_supersede CHECK (
        supersedes_version_id IS NULL
        OR supersedes_version_id <> calculation_version_id
    ),
    CONSTRAINT ck_calculation_version_confirmed_pair CHECK (
        (confirmed_by IS NULL AND confirmed_at IS NULL)
        OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_calculation_version_published_pair CHECK (
        (published_by IS NULL AND published_at IS NULL)
        OR (published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);

-- 本次修改：同一批次/视角只保留一个当前版本指针，历史版本仍以只读记录保留。
CREATE UNIQUE INDEX IF NOT EXISTS uk_calculation_version_current
    ON calc.calculation_version(batch_id, perspective)
    WHERE current_flag = TRUE;

CREATE INDEX IF NOT EXISTS idx_calculation_version_lookup
    ON calc.calculation_version(batch_id, perspective, status, calculated_at);

CREATE INDEX IF NOT EXISTS idx_calculation_version_job
    ON calc.calculation_version(job_id);

-- 本次修改：为旧结果增加可空版本归属；NULL 表示 V007/V009 遗留结果，不执行历史回填。
ALTER TABLE calc.pnl_result
    ADD COLUMN IF NOT EXISTS calculation_version_id VARCHAR(128);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'calc.pnl_result'::regclass
           AND conname = 'fk_pnl_result_calculation_version'
    ) THEN
        ALTER TABLE calc.pnl_result
            ADD CONSTRAINT fk_pnl_result_calculation_version
            FOREIGN KEY (calculation_version_id)
            REFERENCES calc.calculation_version(calculation_version_id);
    END IF;
END
$$;

-- 本次修改：拆分旧批次唯一约束，兼容一条遗留结果并允许同批次保留多个版本结果。
ALTER TABLE calc.pnl_result
    DROP CONSTRAINT IF EXISTS uk_pnl_result;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pnl_result_legacy_scope
    ON calc.pnl_result(batch_id, perspective, scope_code)
    WHERE calculation_version_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pnl_result_version_scope
    ON calc.pnl_result(calculation_version_id, perspective, scope_code)
    WHERE calculation_version_id IS NOT NULL;

-- 本次修改：生命周期事件先以同事务 Outbox 记录落库，投递失败由后续 Worker 重试或转人工。
CREATE TABLE IF NOT EXISTS flow.calculation_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    calculation_version_id VARCHAR(128),
    trace_id VARCHAR(128),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_no INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    CONSTRAINT uk_calculation_event_outbox_event_id UNIQUE (event_id),
    CONSTRAINT fk_calculation_event_outbox_version
        FOREIGN KEY (calculation_version_id)
        REFERENCES calc.calculation_version(calculation_version_id),
    CONSTRAINT ck_calculation_event_outbox_payload_object CHECK (
        jsonb_typeof(payload_json) = 'object'
    ),
    CONSTRAINT ck_calculation_event_outbox_status CHECK (
        delivery_status IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'FAILED', 'MANUAL_REQUIRED')
    ),
    CONSTRAINT ck_calculation_event_outbox_attempt_no CHECK (
        attempt_no >= 0
    ),
    CONSTRAINT ck_calculation_event_outbox_delivered_pair CHECK (
        (delivery_status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (delivery_status <> 'DELIVERED' AND delivered_at IS NULL)
    ),
    CONSTRAINT ck_calculation_event_outbox_failed_error CHECK (
        delivery_status NOT IN ('FAILED', 'MANUAL_REQUIRED')
        OR (last_error IS NOT NULL AND length(btrim(last_error)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_calculation_event_outbox_pending
    ON flow.calculation_event_outbox(delivery_status, next_attempt_at, id)
    WHERE delivery_status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_calculation_event_outbox_aggregate
    ON flow.calculation_event_outbox(aggregate_type, aggregate_id, occurred_at, id);
