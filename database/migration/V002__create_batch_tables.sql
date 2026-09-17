CREATE TABLE IF NOT EXISTS meta.analysis_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(64) NOT NULL UNIQUE,
    period CHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_analysis_batch_period ON meta.analysis_batch(period);
