-- FC 预测数值快照。每个版本可保留多个月份、范围和指标，导入覆盖同一业务唯一键。
CREATE TABLE IF NOT EXISTS calc.fc_snapshot (
    id BIGSERIAL PRIMARY KEY,
    fc_version_id BIGINT NOT NULL REFERENCES calc.fc_version(id),
    period CHAR(7) NOT NULL,
    scope_code VARCHAR(128) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    metric_value NUMERIC(24, 8) NOT NULL,
    source_task_no VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fc_snapshot UNIQUE (fc_version_id, period, scope_code, metric_code)
);

CREATE INDEX IF NOT EXISTS idx_fc_snapshot_query
    ON calc.fc_snapshot(fc_version_id, period, scope_code, metric_code);
