-- V021 flow.export_task immutable input snapshot verification.
-- Run after V021 against PostgreSQL:
--   psql -v ON_ERROR_STOP=1 -f database/verification/check_export_task_snapshot.sql

DO $$
DECLARE
    v_table_oid oid;
    v_column_name text;
BEGIN
    SELECT c.oid INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'export_task'
       AND c.relkind IN ('r', 'p');

    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.export_task';
    END IF;

    FOREACH v_column_name IN ARRAY ARRAY[
        'batch_no', 'result_version', 'perspective', 'result_status',
        'format', 'summary_scopes', 'detail_scopes'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_table_oid AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing snapshot column flow.export_task.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_column_name IN ARRAY ARRAY[
        'ck_export_task_snapshot_complete',
        'ck_export_task_snapshot_perspective',
        'ck_export_task_snapshot_status',
        'ck_export_task_snapshot_format'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid = v_table_oid AND conname = v_column_name
               AND contype = 'c'
        ) THEN
            RAISE EXCEPTION 'Missing snapshot constraint flow.export_task.%', v_column_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_job_id text := 'export-snapshot-verification_' || v_suffix;
    v_summary text[] := ARRAY['NANCHANG', 'WUHAN'];
    v_detail text[] := ARRAY['NANCHANG'];
    v_replay_summary text[];
    v_replay_detail text[];
BEGIN
    INSERT INTO flow.export_task (
        job_id, user_id, request_key, file_name, content_type,
        batch_no, result_version, perspective, result_status, format,
        summary_scopes, detail_scopes, status, expires_at
    ) VALUES (
        v_job_id, 'export-snapshot-verification-user',
        'export-snapshot-verification-request', 'B-VERIFY-R1.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'B-VERIFY', 'R1', 'BASE', 'PUBLISHED', 'XLSX',
        v_summary, v_detail, 'QUEUED', CURRENT_TIMESTAMP + INTERVAL '15 minutes'
    );

    -- The verification intentionally checks the application replay contract
    -- at the SQL level: an idempotent insert must preserve the first snapshot.
    INSERT INTO flow.export_task AS existing (
        job_id, user_id, request_key, file_name, content_type,
        batch_no, result_version, perspective, result_status, format,
        summary_scopes, detail_scopes, status, expires_at
    ) VALUES (
        v_job_id || '_replay', 'export-snapshot-verification-user',
        'export-snapshot-verification-request', 'replay.csv', 'text/csv',
        'B-REPLAY', 'R2', 'BASE', 'PUBLISHED', 'CSV',
        ARRAY['REPLAY']::text[], ARRAY[]::text[], 'QUEUED',
        CURRENT_TIMESTAMP + INTERVAL '15 minutes'
    )
    ON CONFLICT (user_id, request_key) DO UPDATE
       SET updated_at = existing.updated_at;

    SELECT summary_scopes, detail_scopes
      INTO v_replay_summary, v_replay_detail
      FROM flow.export_task
     WHERE user_id = 'export-snapshot-verification-user'
       AND request_key = 'export-snapshot-verification-request';

    IF v_replay_summary <> ARRAY['NANCHANG', 'WUHAN']::text[]
       OR v_replay_detail <> ARRAY['NANCHANG']::text[] THEN
        RAISE EXCEPTION 'Export input snapshot was not preserved on replay';
    END IF;
END
$$;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_rejected boolean := false;
BEGIN
    -- 本次新增负向契约：仅填写七项 snapshot 字段中的 batch_no，必须被完整性约束拒绝。
    BEGIN
        INSERT INTO flow.export_task (
            job_id, user_id, request_key, file_name, content_type,
            batch_no, status, expires_at
        ) VALUES (
            'export-snapshot-partial-verification_' || v_suffix,
            'export-snapshot-partial-verification-user',
            'export-snapshot-partial-verification-request',
            'partial.xlsx',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'B-PARTIAL', 'QUEUED', CURRENT_TIMESTAMP + INTERVAL '15 minutes'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;

    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Partial export input snapshot was accepted unexpectedly';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V021 flow.export_task immutable input snapshot verification passed.'
    AS verification_result;
