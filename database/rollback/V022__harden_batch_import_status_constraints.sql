-- V022 rollback.  This removes only the V022 CHECK constraints; no data is changed.
-- Confirm that callers do not rely on database-side validation before executing.

ALTER TABLE meta.analysis_batch
    DROP CONSTRAINT IF EXISTS ck_analysis_batch_status;

ALTER TABLE flow.import_task
    DROP CONSTRAINT IF EXISTS ck_import_task_status,
    DROP CONSTRAINT IF EXISTS ck_import_task_issue_count_nonnegative,
    DROP CONSTRAINT IF EXISTS ck_import_task_valid_issue_count;
