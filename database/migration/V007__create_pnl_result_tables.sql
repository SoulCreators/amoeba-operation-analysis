CREATE TABLE IF NOT EXISTS calc.pnl_result (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    perspective VARCHAR(32) NOT NULL,
    scope_code VARCHAR(128) NOT NULL,
    result_status VARCHAR(32) NOT NULL DEFAULT 'CALCULATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pnl_result UNIQUE (batch_id, perspective, scope_code)
);

CREATE TABLE IF NOT EXISTS calc.pnl_result_line (
    id BIGSERIAL PRIMARY KEY,
    result_id BIGINT NOT NULL REFERENCES calc.pnl_result(id),
    line_code VARCHAR(64) NOT NULL,
    line_value NUMERIC(24, 8),
    CONSTRAINT uk_pnl_result_line UNIQUE (result_id, line_code)
);
