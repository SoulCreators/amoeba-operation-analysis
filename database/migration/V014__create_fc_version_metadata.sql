-- FC 版本治理元数据。预测数值快照待后续导入链路接入，不能由 ACT/BGT 静默替代。
CREATE TABLE IF NOT EXISTS calc.fc_version (
    id BIGSERIAL PRIMARY KEY,
    version_code VARCHAR(64) NOT NULL,
    perspective VARCHAR(32) NOT NULL,
    base_period CHAR(7) NOT NULL,
    coverage_start CHAR(7) NOT NULL,
    coverage_end CHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    uploaded_by VARCHAR(64) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    official BOOLEAN NOT NULL DEFAULT FALSE,
    published_by VARCHAR(64),
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fc_version UNIQUE (version_code, perspective, base_period),
    CONSTRAINT ck_fc_period_range CHECK (coverage_start <= coverage_end)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fc_official_version
    ON calc.fc_version(perspective, base_period)
    WHERE official = TRUE;

CREATE INDEX IF NOT EXISTS idx_fc_version_lookup
    ON calc.fc_version(perspective, base_period, status, official);
