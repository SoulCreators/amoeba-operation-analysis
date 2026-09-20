CREATE TABLE IF NOT EXISTS calc.ai_analysis_draft (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    result_version_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    generated_by VARCHAR(64) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by VARCHAR(64),
    published_at TIMESTAMP,
    prompt_version VARCHAR(64),
    model_code VARCHAR(128),
    sections_json TEXT NOT NULL,
    CONSTRAINT uk_ai_analysis_batch_version UNIQUE (batch_id, result_version_no)
);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_status
    ON calc.ai_analysis_draft(batch_id, status);
