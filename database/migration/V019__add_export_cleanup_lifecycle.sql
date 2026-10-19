-- V019 export file cleanup lifecycle. Task rows are retained for audit.
ALTER TABLE flow.export_task
    ADD COLUMN IF NOT EXISTS cleanup_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS cleanup_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cleanup_claim_token VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cleanup_lease_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cleanup_next_attempt_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cleanup_last_error VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS cleanup_completed_at TIMESTAMP;

ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_status,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_attempts,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_claim,
    DROP CONSTRAINT IF EXISTS ck_export_task_cleanup_completed;

ALTER TABLE flow.export_task
    ADD CONSTRAINT ck_export_task_cleanup_status CHECK (
        cleanup_status IN ('NOT_REQUIRED', 'PENDING', 'IN_PROGRESS', 'DELETED', 'FAILED')
    ),
    ADD CONSTRAINT ck_export_task_cleanup_attempts CHECK (
        cleanup_attempts BETWEEN 0 AND 3
    ),
    ADD CONSTRAINT ck_export_task_cleanup_claim CHECK (
        cleanup_status <> 'IN_PROGRESS'
        OR (cleanup_claim_token IS NOT NULL AND cleanup_lease_until IS NOT NULL)
    ),
    ADD CONSTRAINT ck_export_task_cleanup_completed CHECK (
        cleanup_status <> 'DELETED' OR cleanup_completed_at IS NOT NULL
    );

UPDATE flow.export_task
   SET cleanup_status = 'PENDING',
       cleanup_next_attempt_at = COALESCE(cleanup_next_attempt_at, CURRENT_TIMESTAMP)
 WHERE status IN ('SUCCEEDED', 'EXPIRED')
   AND expires_at <= CURRENT_TIMESTAMP
   AND cleanup_status = 'NOT_REQUIRED';

CREATE INDEX IF NOT EXISTS idx_export_task_cleanup_claim
    ON flow.export_task(cleanup_status, cleanup_next_attempt_at,
                        cleanup_lease_until, expires_at, id)
    WHERE status = 'EXPIRED'
      AND cleanup_status IN ('PENDING', 'IN_PROGRESS');
