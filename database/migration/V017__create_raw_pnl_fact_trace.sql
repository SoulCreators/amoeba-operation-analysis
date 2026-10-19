CREATE TABLE IF NOT EXISTS raw.pnl_fact_trace (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES meta.analysis_batch(id),
    perspective VARCHAR(32) NOT NULL,
    source_task_no VARCHAR(64) NOT NULL REFERENCES flow.import_task(task_no),
    source_file_name VARCHAR(255) NOT NULL,
    source_checksum VARCHAR(128),
    source_sheet_name VARCHAR(255) NOT NULL,
    source_row_no INTEGER NOT NULL CHECK (source_row_no >= 1),
    scope_code VARCHAR(128) NOT NULL,
    base_code VARCHAR(128),
    product_code VARCHAR(128),
    line_code VARCHAR(128),
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pnl_fact_trace_task_sheet_row
        UNIQUE (source_task_no, source_sheet_name, source_row_no)
);

CREATE INDEX IF NOT EXISTS idx_pnl_fact_trace_batch_perspective_scope
    ON raw.pnl_fact_trace(batch_id, perspective, scope_code);

CREATE INDEX IF NOT EXISTS idx_pnl_fact_trace_task_row
    ON raw.pnl_fact_trace(source_task_no, source_row_no);
