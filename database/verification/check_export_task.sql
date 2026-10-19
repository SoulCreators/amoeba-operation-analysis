-- V018 flow.export_task PostgreSQL structure and idempotency verification.
--
-- Run against a database where migrations V001-V018 have been applied:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_export_task.sql
--
-- The final transaction is rolled back, so the idempotency probe leaves no data.

DO $$
DECLARE
    v_table_oid oid;
    v_constraint_oid oid;
    v_index_oid oid;
    v_column_name text;
    v_required_columns text[] := ARRAY[
        'id', 'job_id', 'user_id', 'request_key', 'file_name', 'content_type',
        'status', 'created_at', 'updated_at', 'expires_at'
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
            SELECT 1
              FROM pg_attribute
             WHERE attrelid = v_table_oid
               AND attname = v_column_name
               AND NOT attisdropped
               AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.export_task.%', v_column_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'flow'
           AND table_name = 'export_task'
           AND column_name = ANY(v_required_columns)
           AND is_nullable <> 'NO'
    ) THEN
        RAISE EXCEPTION 'Required flow.export_task column is nullable';
    END IF;

    SELECT con.oid
      INTO v_constraint_oid
      FROM pg_constraint con
     WHERE con.conrelid = v_table_oid
       AND con.conname = 'uk_export_task_user_request'
       AND con.contype = 'u';
    IF v_constraint_oid IS NULL THEN
        RAISE EXCEPTION 'Missing idempotency constraint flow.export_task.uk_export_task_user_request';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_index i
          JOIN pg_class idx ON idx.oid = i.indexrelid
         WHERE i.indrelid = v_table_oid
           AND i.indisunique
           AND pg_get_indexdef(i.indexrelid) ILIKE '%job_id%'
    ) THEN
        RAISE EXCEPTION 'Missing unique job_id constraint/index on flow.export_task';
    END IF;

    FOREACH v_column_name IN ARRAY ARRAY[
        'ck_export_task_status',
        'ck_export_task_expiry',
        'ck_export_task_failed_message'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_constraint con
             WHERE con.conrelid = v_table_oid
               AND con.conname = v_column_name
               AND con.contype = 'c'
        ) THEN
            RAISE EXCEPTION 'Missing check constraint flow.export_task.%', v_column_name;
        END IF;
    END LOOP;

    SELECT indexrelid
      INTO v_index_oid
      FROM pg_index i
      JOIN pg_class idx ON idx.oid = i.indexrelid
     WHERE i.indrelid = v_table_oid
       AND idx.relname = 'idx_export_task_user_status';
    IF v_index_oid IS NULL THEN
        RAISE EXCEPTION 'Missing index flow.export_task.idx_export_task_user_status';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix TEXT := substr(md5(clock_timestamp()::TEXT || random()::TEXT), 1, 16);
    v_duplicate_rejected BOOLEAN := FALSE;
BEGIN
    INSERT INTO flow.export_task (
        job_id, user_id, request_key, file_name, content_type, status, expires_at
    ) VALUES (
        'export-task-verification_' || v_suffix,
        'export-task-verification-user',
        'export-task-verification-request',
        'verification.csv',
        'text/csv',
        'QUEUED',
        CURRENT_TIMESTAMP + INTERVAL '15 minutes'
    );

    BEGIN
        INSERT INTO flow.export_task (
            job_id, user_id, request_key, file_name, content_type, status, expires_at
        ) VALUES (
            'export-task-verification-retry_' || v_suffix,
            'export-task-verification-user',
            'export-task-verification-request',
            'verification-retry.csv',
            'text/csv',
            'QUEUED',
            CURRENT_TIMESTAMP + INTERVAL '15 minutes'
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := TRUE;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected duplicate (user_id, request_key) to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V018 flow.export_task structure and idempotency verification passed.'
    AS verification_result;
