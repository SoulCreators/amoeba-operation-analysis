ALTER TABLE calc.pnl_result
    ADD COLUMN IF NOT EXISTS calculated_at TIMESTAMP;

UPDATE calc.pnl_result
SET calculated_at = COALESCE(calculated_at, created_at)
WHERE calculated_at IS NULL;
