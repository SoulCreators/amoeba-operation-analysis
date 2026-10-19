-- Roll back V019 after confirming no cleanup worker depends on these columns.
ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_completed,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_claim,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_attempts,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_status;

DROP INDEX IF EXISTS flow.idx_export_task_cleanup_claim;

ALTER TABLE flow.export_task
    DROP COLUMN IF EXISTS cleanup_completed_at,
    DROP COLUMN IF EXISTS cleanup_last_error,
    DROP COLUMN IF EXISTS cleanup_next_attempt_at,
    DROP COLUMN IF EXISTS cleanup_lease_until,
    DROP COLUMN IF EXISTS cleanup_claim_token,
    DROP COLUMN IF EXISTS cleanup_attempts,
    DROP COLUMN IF EXISTS cleanup_status;
