-- V037 flow.calculation_job 阶段/心跳列与 flow.calculation_stage_log 结构约束探针。
-- 本次修改：只检查表、字段、约束和外键行为；探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_calculation_stage_log.sql

DO $$
DECLARE
    v_job_oid oid;
    v_log_oid oid;
    v_column_name text;
    v_job_columns text[] := ARRAY[
        'stage', 'heartbeat_at', 'error_code', 'trace_id', 'processed_rows', 'issue_count'
    ];
    v_log_columns text[] := ARRAY[
        'id', 'job_id', 'stage', 'status', 'started_at', 'finished_at',
        'processed_rows', 'control_amount', 'issue_count', 'error_code', 'trace_id', 'message'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_calculation_job_stage',
        'ck_calculation_job_processed_rows',
        'ck_calculation_job_issue_count',
        'uk_calculation_stage_log_job_stage',
        'fk_calculation_stage_log_job',
        'ck_calculation_stage_log_stage',
        'ck_calculation_stage_log_status',
        'ck_calculation_stage_log_processed_rows',
        'ck_calculation_stage_log_issue_count',
        'ck_calculation_stage_log_finished_at',
        'ck_calculation_stage_log_failed_error'
    ];
BEGIN
    SELECT c.oid INTO v_job_oid
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'calculation_job' AND c.relkind IN ('r', 'p');

    IF v_job_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.calculation_job';
    END IF;

    SELECT c.oid INTO v_log_oid
      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'calculation_stage_log' AND c.relkind IN ('r', 'p');

    IF v_log_oid IS NULL THEN
        RAISE EXCEPTION 'Missing table flow.calculation_stage_log';
    END IF;

    FOREACH v_column_name IN ARRAY v_job_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_job_oid AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.calculation_job.%', v_column_name;
        END IF;
    END LOOP;

    FOREACH v_column_name IN ARRAY v_log_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_attribute
             WHERE attrelid = v_log_oid AND attname = v_column_name
               AND NOT attisdropped AND attnum > 0
        ) THEN
            RAISE EXCEPTION 'Missing column flow.calculation_stage_log.%', v_column_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'flow' AND table_name = 'calculation_job'
           AND column_name IN ('processed_rows', 'issue_count') AND is_nullable <> 'NO'
    ) THEN
        RAISE EXCEPTION 'Required flow.calculation_job counter column is nullable';
    END IF;

    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE (conrelid = v_job_oid OR conrelid = v_log_oid)
               AND conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = v_log_oid AND conname = 'fk_calculation_stage_log_job'
           AND contype <> 'f'
    ) THEN
        RAISE EXCEPTION 'fk_calculation_stage_log_job must be a foreign key';
    END IF;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_prefix text := 'stage-log-verification-' || v_suffix;
    v_job_id text := v_prefix || '-job';
    v_rejected boolean := false;
BEGIN
    INSERT INTO flow.calculation_job (job_id, request_key, batch_no, status, attempt_no)
    VALUES (v_job_id, v_prefix || '-request', v_prefix || '-batch', 'RUNNING', 1);

    -- 本次修改：阶段日志可按 (job_id, stage) 幂等覆盖，RUNNING 推进为终态。
    INSERT INTO flow.calculation_stage_log (job_id, stage, status, started_at, processed_rows)
    VALUES (v_job_id, 'NORMALIZE', 'RUNNING', CURRENT_TIMESTAMP, 100);

    INSERT INTO flow.calculation_stage_log (
        job_id, stage, status, started_at, finished_at, processed_rows, control_amount, issue_count, trace_id
    ) VALUES (
        v_job_id, 'NORMALIZE', 'SUCCEEDED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 100, 12345.67, 0, v_prefix || '-trace'
    )
    ON CONFLICT (job_id, stage) DO UPDATE SET
        status = EXCLUDED.status,
        started_at = EXCLUDED.started_at,
        finished_at = EXCLUDED.finished_at,
        processed_rows = EXCLUDED.processed_rows,
        control_amount = EXCLUDED.control_amount,
        issue_count = EXCLUDED.issue_count,
        trace_id = EXCLUDED.trace_id;

    IF NOT EXISTS (
        SELECT 1 FROM flow.calculation_stage_log
         WHERE job_id = v_job_id AND stage = 'NORMALIZE' AND status = 'SUCCEEDED'
    ) THEN
        RAISE EXCEPTION 'Expected stage log upsert to finalize RUNNING stage';
    END IF;

    -- 本次修改：失败阶段必须携带非空错误码。
    BEGIN
        INSERT INTO flow.calculation_stage_log (job_id, stage, status)
        VALUES (v_job_id, 'RECONCILE', 'FAILED');
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected FAILED stage log without error_code to be rejected';
    END IF;

    -- 本次修改：未知阶段被阶段取值约束拒绝。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_stage_log (job_id, stage, status)
        VALUES (v_job_id, 'UNKNOWN', 'RUNNING');
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown stage to be rejected';
    END IF;

    -- 本次修改：作业阶段取值约束拒绝未知阶段。
    v_rejected := false;
    BEGIN
        UPDATE flow.calculation_job SET stage = 'UNKNOWN' WHERE job_id = v_job_id;
    EXCEPTION
        WHEN check_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected unknown job stage to be rejected';
    END IF;

    -- 本次修改：阶段日志外键阻止引用不存在的作业。
    v_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_stage_log (job_id, stage, status)
        VALUES (v_prefix || '-missing-job', 'MAP', 'RUNNING');
    EXCEPTION
        WHEN foreign_key_violation THEN
            v_rejected := true;
    END;
    IF NOT v_rejected THEN
        RAISE EXCEPTION 'Expected stage log referencing missing job to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V037 flow.calculation_job stage/heartbeat and flow.calculation_stage_log verification passed.'
    AS verification_result;
