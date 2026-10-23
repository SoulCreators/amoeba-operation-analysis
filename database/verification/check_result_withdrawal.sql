-- V042 结果撤回结构与约束探针。
-- 检查 calc.pnl_result 撤回痕迹列，以及导出任务与计算版本状态约束对 WITHDRAWN 的放行。
-- 探针数据置于事务中并最终回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_result_withdrawal.sql

DO $$
DECLARE
    v_column_name text;
    v_columns text[] := ARRAY[
        'withdrawn_by', 'withdrawn_at', 'withdraw_reason', 'notification_targets'
    ];
    v_constraint_name text;
    v_constraints text[] := ARRAY[
        'ck_export_task_snapshot_status',
        'ck_calculation_version_status'
    ];
BEGIN
    -- calc.pnl_result 撤回痕迹列。
    FOREACH v_column_name IN ARRAY v_columns LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
             WHERE table_schema = 'calc'
               AND table_name = 'pnl_result'
               AND column_name = v_column_name
        ) THEN
            RAISE EXCEPTION 'Missing column calc.pnl_result.%', v_column_name;
        END IF;
    END LOOP;

    -- 两个被放行的状态约束必须仍然存在。
    FOREACH v_constraint_name IN ARRAY v_constraints LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
             WHERE c.conname = v_constraint_name
               AND t.relname IN ('export_task', 'calculation_version')
        ) THEN
            RAISE EXCEPTION 'Missing constraint %', v_constraint_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_batch_id bigint;
    v_batch_no text;
    v_job_id text;
    v_request_key text;
    v_snapshot_id text;
    v_version_id text;
    v_withdrawn_accepted boolean := false;
    v_unknown_rejected boolean := false;
BEGIN
    v_batch_no := 'v042-verification-batch-' || v_suffix;
    v_job_id := 'v042-verification-job-' || v_suffix;
    v_request_key := 'v042-verification-request-' || v_suffix;
    v_snapshot_id := 'v042-verification-snapshot-' || v_suffix;
    v_version_id := 'v042-verification-version-' || v_suffix;

    INSERT INTO meta.analysis_batch (
        batch_no, period, status, perspective, created_by
    ) VALUES (
        v_batch_no, '2099-12', 'DRAFT', 'BASE', 'v042-verification'
    ) RETURNING id INTO v_batch_id;

    INSERT INTO flow.calculation_job (
        job_id, request_key, batch_no, status, attempt_no
    ) VALUES (
        v_job_id, v_request_key, v_batch_no, 'QUEUED', 1
    );

    INSERT INTO calc.calculation_snapshot (
        snapshot_id, snapshot_type, schema_version, content_json,
        source_version_set, content_hash, source_version_hash, rule_version, created_by
    ) VALUES (
        v_snapshot_id, 'INPUT', 'ORDINARY-1', '{"formula":"verification"}'::jsonb,
        '["TASK-V042"]'::jsonb, 'hash-content-v042', 'hash-source-v042',
        'rule-v042', 'v042-verification'
    );

    -- WITHDRAWN 被放行：撤回状态应能写入版本头与结果明细。
    v_withdrawn_accepted := true;
    BEGIN
        INSERT INTO calc.calculation_version (
            calculation_version_id, batch_id, batch_no, perspective, version_no,
            job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
            status, calculated_by
        ) VALUES (
            v_version_id, v_batch_id, v_batch_no, 'BASE', 'V042-1',
            v_job_id, v_snapshot_id, 'hash-source-v042', 'hash-config-v042',
            'hash-input-v042', 'WITHDRAWN', 'v042-verification'
        );

        INSERT INTO calc.pnl_result (
            batch_id, perspective, scope_code, result_status, version_no,
            calculation_version_id, withdrawn_by, withdraw_reason, notification_targets
        ) VALUES (
            v_batch_id, 'BASE', 'ALL', 'WITHDRAWN', 'V042-1',
            v_version_id, 'v042-verification', 'probe withdrawal', 'finance-lead'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_withdrawn_accepted := false;
    END;

    IF NOT v_withdrawn_accepted THEN
        RAISE EXCEPTION 'Expected WITHDRAWN status to be accepted';
    END IF;

    -- 未知状态仍被拒绝：状态约束只是放行 WITHDRAWN，不是移除约束。
    BEGIN
        INSERT INTO calc.calculation_version (
            calculation_version_id, batch_id, batch_no, perspective, version_no,
            job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
            status, calculated_by
        ) VALUES (
            'v042-verification-bad-' || v_suffix, v_batch_id, v_batch_no, 'BASE', 'V042-bad',
            v_job_id, v_snapshot_id, 'hash-source-v042', 'hash-config-v042',
            'hash-input-v042', 'UNKNOWN', 'v042-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_unknown_rejected := true;
    END;

    IF NOT v_unknown_rejected THEN
        RAISE EXCEPTION 'Expected unknown calculation version status to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V042 result withdrawal verification passed.' AS verification_result;
