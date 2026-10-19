-- V019 flow.export_task cleanup lifecycle and claim protocol verification.
--
-- Run against a database where migrations V001-V019 have been applied:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_export_cleanup.sql
--
-- The transaction is rolled back, so the probe leaves no task rows behind.

DO $$
DECLARE
    v_table_oid oid;
    v_column_name text;
    v_required_columns text[] := ARRAY[
        'cleanup_status', 'cleanup_attempts', 'cleanup_claim_token',
        'cleanup_lease_until', 'cleanup_next_attempt_at',
        'cleanup_last_error', 'cleanup_completed_at'
    ];
BEGIN
    SELECT c.oid
      INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow'
       AND c.relname = 'export_task'
       AND c.relkind IN ('r', 'p');

    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.export_task';
    END IF;

    FOREACH v_column_name IN ARRAY v_required_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table_oid
               AND attname = v_column_name
               AND NOT attisdropped
               AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.export_task.%', v_column_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'flow'
           AND table_name = 'export_task'
           AND column_name IN ('cleanup_status', 'cleanup_attempts')
           AND is_nullable <> 'NO'
    ) THEN
        RAISE EXCEPTION 'cleanup_status and cleanup_attempts must be NOT NULL';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = v_table_oid
           AND conname = 'ck_export_task_cleanup_status'
           AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'Missing cleanup status check constraint';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = v_table_oid
           AND conname = 'ck_export_task_cleanup_attempts'
           AND contype = 'c'
    ) THEN
        RAISE EXCEPTION 'Missing cleanup attempts check constraint';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_class idx
          JOIN pg_namespace n ON n.oid = idx.relnamespace
         WHERE n.nspname = 'flow'
           AND idx.relname = 'idx_export_task_cleanup_claim'
    ) THEN
        RAISE EXCEPTION 'Missing index flow.idx_export_task_cleanup_claim';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_prefix text;
    v_now timestamp := clock_timestamp()::timestamp;
    v_claim_job text;
    v_claim_token text;
    v_attempt integer;
    v_deleted_count integer;
BEGIN
    v_prefix := 'export-cleanup-verification-' || v_suffix;

    INSERT INTO flow.export_task (
        job_id, user_id, request_key, file_name, content_type, status, created_at, updated_at, expires_at
    ) VALUES
        (v_prefix || '-queued', v_prefix || '-user-q', v_prefix || '-key-q',
         'queued.csv', 'text/csv', 'QUEUED', v_now - INTERVAL '2 minutes', v_now - INTERVAL '2 minutes', v_now - INTERVAL '1 minute'),
        (v_prefix || '-running', v_prefix || '-user-r', v_prefix || '-key-r',
         'running.csv', 'text/csv', 'RUNNING', v_now - INTERVAL '2 minutes', v_now - INTERVAL '2 minutes', v_now - INTERVAL '1 minute'),
        (v_prefix || '-success', v_prefix || '-user-s', v_prefix || '-key-s',
         'success.csv', 'text/csv', 'SUCCEEDED', v_now - INTERVAL '2 minutes', v_now - INTERVAL '2 minutes', v_now - INTERVAL '1 minute');

    UPDATE flow.export_task
       SET status = 'EXPIRED',
           cleanup_status = CASE WHEN status IN ('SUCCEEDED', 'EXPIRED')
                                      AND cleanup_status = 'NOT_REQUIRED' THEN 'PENDING'
                                 ELSE cleanup_status END,
           cleanup_next_attempt_at = CASE WHEN status IN ('SUCCEEDED', 'EXPIRED')
                                               AND cleanup_status = 'NOT_REQUIRED' THEN v_now
                                          ELSE cleanup_next_attempt_at END,
           cleanup_claim_token = NULL,
           cleanup_lease_until = NULL,
           updated_at = v_now
     WHERE job_id LIKE v_prefix || '%'
       AND (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED')
            OR (status = 'EXPIRED' AND cleanup_status = 'NOT_REQUIRED'))
       AND expires_at <= v_now;

    IF (SELECT cleanup_status FROM flow.export_task WHERE job_id = v_prefix || '-queued')
       <> 'NOT_REQUIRED'
       OR (SELECT cleanup_status FROM flow.export_task WHERE job_id = v_prefix || '-running')
       <> 'NOT_REQUIRED'
       OR (SELECT cleanup_status FROM flow.export_task WHERE job_id = v_prefix || '-success')
       <> 'PENDING' THEN
        RAISE EXCEPTION 'Due-state transition did not isolate successful file cleanup';
    END IF;

    WITH candidates AS (
        SELECT id, md5(random()::text || clock_timestamp()::text || id::text) AS claim_token
          FROM flow.export_task
         WHERE job_id = v_prefix || '-success'
           AND status = 'EXPIRED'
           AND cleanup_status = 'PENDING'
           AND cleanup_attempts < 3
           AND cleanup_next_attempt_at <= v_now
         ORDER BY id
         LIMIT 1 FOR UPDATE SKIP LOCKED
    )
    UPDATE flow.export_task AS task
       SET cleanup_status = 'IN_PROGRESS',
           cleanup_attempts = task.cleanup_attempts + 1,
           cleanup_claim_token = candidates.claim_token,
           cleanup_lease_until = v_now + INTERVAL '5 minutes',
           cleanup_next_attempt_at = NULL,
           updated_at = v_now
      FROM candidates
     WHERE task.id = candidates.id
    RETURNING task.job_id, task.cleanup_claim_token, task.cleanup_attempts
         INTO v_claim_job, v_claim_token, v_attempt;

    IF v_claim_job <> v_prefix || '-success' OR v_attempt <> 1
       OR v_claim_token IS NULL THEN
        RAISE EXCEPTION 'Cleanup claim did not create a token, lease, and first attempt';
    END IF;

    UPDATE flow.export_task
       SET cleanup_status = 'DELETED',
           cleanup_claim_token = NULL,
           cleanup_lease_until = NULL,
           cleanup_next_attempt_at = NULL,
           cleanup_last_error = NULL,
           cleanup_completed_at = v_now,
           updated_at = v_now
     WHERE job_id = v_claim_job
       AND status = 'EXPIRED'
       AND cleanup_status = 'IN_PROGRESS'
       AND cleanup_claim_token = v_claim_token;

    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    IF v_deleted_count <> 1 OR NOT EXISTS (
        SELECT 1 FROM flow.export_task WHERE job_id = v_prefix || '-success'
    ) THEN
        RAISE EXCEPTION 'Successful cleanup must retain the task row';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V019 flow.export_task cleanup lifecycle and claim verification passed.'
    AS verification_result;
