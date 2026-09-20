ALTER TABLE calc.pnl_result
    ADD COLUMN IF NOT EXISTS version_no VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confirmed_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS published_by VARCHAR(64),
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

UPDATE calc.pnl_result
SET version_no = COALESCE(version_no, 'LEGACY-' || id::VARCHAR)
WHERE version_no IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pnl_result_version
    ON calc.pnl_result(batch_id, version_no);
