-- V021 rollback.  Confirm no worker deployment still reads these columns
-- before executing this script.
ALTER TABLE flow.export_task
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_format,
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_status,
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_perspective,
    DROP CONSTRAINT IF EXISTS ck_export_task_snapshot_complete;

ALTER TABLE flow.export_task
    DROP COLUMN IF EXISTS detail_scopes,
    DROP COLUMN IF EXISTS summary_scopes,
    DROP COLUMN IF EXISTS format,
    DROP COLUMN IF EXISTS result_status,
    DROP COLUMN IF EXISTS perspective,
    DROP COLUMN IF EXISTS result_version,
    DROP COLUMN IF EXISTS batch_no;
