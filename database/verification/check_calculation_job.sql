-- V025 flow.calculation_job 结构与约束探针。
-- 本次修改：只检查表、字段、约束和活动 request_key 唯一行为；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_calculation_job.sql

DO $$
DECLARE
    v_table_oid oid;
    v_index_oid oid;
    v_column_name text;
    v_required_columns text[] := ARRAY[
        'id', 'job_id', 'request_key', 'batch_no', 'status', 'attempt_no',
        'last_error', 'created_at', 'started_at', 'finished_at'
    ];
    v_required_not_null text[] := ARRAY[
        'id', 'job_id', 'request_key', 'batch_no', 'status', 'attempt_no', 'created_at'
    ];
    v_constraint_name text;
BEGIN
    SELECT c.oid
      INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow'
       AND c.relname = 'calculation_job'
       AND c.relkind IN ('r', 'p');

    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.calculation_job';
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
            RAISE EXCEPTION 'Missing column flow.calculation_job.%', v_column_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'flow'
           AND table_name = 'calculation_job'
           AND column_name = ANY(v_required_not_null)
           AND is_nullable <> 'NO'
    ) THEN
        RAISE EXCEPTION 'Required flow.calculation_job column is nullable';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = v_table_oid
           AND conname = 'uk_calculation_job_job_id'
           AND contype = 'u'
    ) THEN
        RAISE EXCEPTION 'Missing unique job_id constraint flow.calculation_job.uk_calculation_job_job_id';
    END IF;

    FOREACH v_constraint_name IN ARRAY ARRAY[
        'ck_calculation_job_status',
        'ck_calculation_job_attempt_no',
        'ck_calculation_job_started_at',
        'ck_calculation_job_finished_at',
        'ck_calculation_job_failed_error'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_constraint
             WHERE conrelid = v_table_oid
               AND conname = v_constraint_name
               AND contype = 'c'
        ) THEN
            RAISE EXCEPTION 'Missing check constraint flow.calculation_job.%', v_constraint_name;
        END IF;
    END LOOP;

    SELECT i.indexrelid
      INTO v_index_oid
      FROM pg_index i
      JOIN pg_class idx ON idx.oid = i.indexrelid
     WHERE i.indrelid = v_table_oid
       AND idx.relname = 'uk_calculation_job_active_request_key'
       AND i.indisunique
       AND pg_get_indexdef(i.indexrelid) ILIKE '%request_key%'
       AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%status%'
       AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%QUEUED%'
       AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%RUNNING%';

    IF v_index_oid IS NULL THEN
        RAISE EXCEPTION 'Missing unique active request_key index flow.uk_calculation_job_active_request_key';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_prefix text;
    v_duplicate_rejected boolean := false;
    v_invalid_rejected boolean := false;
BEGIN
    v_prefix := 'calculation-job-verification-' || v_suffix;

    -- 本次修改：验证活动状态下 request_key 冲突会被数据库拒绝。
    INSERT INTO flow.calculation_job (
        job_id, request_key, batch_no, status, attempt_no
    ) VALUES (
        v_prefix || '-active', v_prefix || '-request', v_prefix || '-batch', 'QUEUED', 1
    );

    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no
        ) VALUES (
            v_prefix || '-duplicate-active', v_prefix || '-request',
            v_prefix || '-batch-duplicate', 'RUNNING', 1
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := true;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected active request_key duplicate to be rejected';
    END IF;

    -- 本次修改：验证 job_id 即使对应不同 request_key 也不能重复。
    v_duplicate_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no, last_error
        ) VALUES (
            v_prefix || '-active', v_prefix || '-job-id-request',
            v_prefix || '-job-id-batch', 'FAILED', 1, 'duplicate job id probe'
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := true;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected duplicate job_id to be rejected';
    END IF;

    -- 本次修改：验证终态不占用活动 request_key，且终态错误字段可按模型保存。
    INSERT INTO flow.calculation_job (
        job_id, request_key, batch_no, status, attempt_no, last_error
    ) VALUES (
        v_prefix || '-failed', v_prefix || '-request', v_prefix || '-batch-failed',
        'FAILED', 1, 'verification failure'
    );

    UPDATE flow.calculation_job
       SET status = 'SUCCEEDED'
     WHERE job_id = v_prefix || '-active';

    INSERT INTO flow.calculation_job (
        job_id, request_key, batch_no, status, attempt_no
    ) VALUES (
        v_prefix || '-released', v_prefix || '-request', v_prefix || '-batch-released', 'QUEUED', 1
    );

    -- 本次修改：验证状态、attempt_no、失败错误和时间顺序约束均生效。
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no
        ) VALUES (
            v_prefix || '-bad-status', v_prefix || '-bad-status-request',
            v_prefix || '-bad-status-batch', 'UNKNOWN', 1
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected invalid calculation job status to be rejected';
    END IF;

    v_invalid_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no
        ) VALUES (
            v_prefix || '-bad-attempt', v_prefix || '-bad-attempt-request',
            v_prefix || '-bad-attempt-batch', 'QUEUED', 0
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected attempt_no below one to be rejected';
    END IF;

    v_invalid_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no, last_error
        ) VALUES (
            v_prefix || '-bad-error', v_prefix || '-bad-error-request',
            v_prefix || '-bad-error-batch', 'FAILED', 1, '   '
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected blank failure error to be rejected';
    END IF;

    v_invalid_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no,
            created_at, started_at
        ) VALUES (
            v_prefix || '-bad-time', v_prefix || '-bad-time-request',
            v_prefix || '-bad-time-batch', 'RUNNING', 1,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 second'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected started_at before created_at to be rejected';
    END IF;

    v_invalid_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_job (
            job_id, request_key, batch_no, status, attempt_no,
            created_at, started_at, finished_at
        ) VALUES (
            v_prefix || '-bad-finished-time', v_prefix || '-bad-finished-request',
            v_prefix || '-bad-finished-batch', 'RUNNING', 1,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 second'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected finished_at before started_at to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V025 flow.calculation_job structure and constraint verification passed.'
    AS verification_result;
