-- V026 计算版本、快照和 Outbox 结构/约束验证。
-- 本次修改：结构探针和行为探针均放在事务中，脚本结束只回滚临时数据，不提交业务记录。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_calculation_version_snapshot_outbox.sql

DO $$
DECLARE
    v_required_table text;
BEGIN
    FOREACH v_required_table IN ARRAY ARRAY[
        'calc.calculation_snapshot',
        'calc.calculation_version',
        'flow.calculation_event_outbox'
    ] LOOP
        IF to_regclass(v_required_table) IS NULL THEN
            RAISE EXCEPTION 'Missing V026 table %', v_required_table;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_schema = 'calc'
           AND table_name = 'pnl_result'
           AND column_name = 'calculation_version_id'
    ) THEN
        RAISE EXCEPTION 'Missing calc.pnl_result.calculation_version_id';
    END IF;

    FOREACH v_required_table IN ARRAY ARRAY[
        'uk_calculation_version_batch_version',
        'uk_calculation_version_current',
        'uk_pnl_result_legacy_scope',
        'uk_pnl_result_version_scope',
        'uk_calculation_event_outbox_event_id'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE c.relname = v_required_table
               AND n.nspname IN ('calc', 'flow')
               AND c.relkind IN ('i', 'r')
        ) THEN
            RAISE EXCEPTION 'Missing V026 index or constraint object %', v_required_table;
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
    v_duplicate_rejected boolean := false;
    v_invalid_rejected boolean := false;
BEGIN
    v_batch_no := 'v026-verification-batch-' || v_suffix;
    v_job_id := 'v026-verification-job-' || v_suffix;
    v_request_key := 'v026-verification-request-' || v_suffix;
    v_snapshot_id := 'v026-verification-snapshot-' || v_suffix;
    v_version_id := 'v026-verification-version-' || v_suffix;

    -- 本次修改：建立最小临时批次和作业，验证版本头能够追溯既有计算作业。
    INSERT INTO meta.analysis_batch (
        batch_no, period, status, perspective, created_by
    ) VALUES (
        v_batch_no, '2099-12', 'DRAFT', 'BASE', 'v026-verification'
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
        v_snapshot_id, 'INPUT', 'V026-1', '{"formula":"verification"}'::jsonb,
        '["TASK-V026"]'::jsonb, 'hash-content-v026', 'hash-source-v026',
        'rule-v026', 'v026-verification'
    );

    INSERT INTO calc.calculation_version (
        calculation_version_id, batch_id, batch_no, perspective, version_no,
        job_id, snapshot_id, source_version_hash, config_hash, input_fingerprint,
        status, calculated_by, trace_id
    ) VALUES (
        v_version_id, v_batch_id, v_batch_no, 'BASE', 'V026-1',
        v_job_id, v_snapshot_id, 'hash-source-v026', 'hash-config-v026',
        'hash-input-v026', 'CALCULATED', 'v026-verification', 'trace-v026'
    );

    -- 本次修改：验证同一结果版本下的结果范围唯一，且结果行可以引用版本头。
    INSERT INTO calc.pnl_result (
        batch_id, perspective, scope_code, result_status, version_no, calculation_version_id
    ) VALUES (
        v_batch_id, 'BASE', 'ALL', 'CALCULATED', 'V026-1', v_version_id
    );

    INSERT INTO flow.calculation_event_outbox (
        event_id, aggregate_type, aggregate_id, event_type,
        calculation_version_id, trace_id, payload_json
    ) VALUES (
        'v026-verification-event-' || v_suffix, 'CALCULATION_VERSION',
        v_version_id, 'CALCULATED', v_version_id, 'trace-v026',
        '{"status":"CALCULATED"}'::jsonb
    );

    BEGIN
        INSERT INTO calc.pnl_result (
            batch_id, perspective, scope_code, result_status, version_no, calculation_version_id
        ) VALUES (
            v_batch_id, 'BASE', 'ALL', 'CALCULATED', 'V026-1', v_version_id
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := true;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected duplicate version result scope to be rejected';
    END IF;

    -- 本次修改：验证 JSONB 类型和 Outbox 状态不变量，避免不可投递记录静默落库。
    BEGIN
        INSERT INTO calc.calculation_snapshot (
            snapshot_id, snapshot_type, schema_version, content_json,
            source_version_set, content_hash, created_by
        ) VALUES (
            'v026-verification-bad-snapshot-' || v_suffix, 'INPUT', 'V026-1',
            '[]'::jsonb, '[]'::jsonb, 'hash-bad-v026', 'v026-verification'
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected non-object calculation snapshot content to be rejected';
    END IF;

    v_invalid_rejected := false;
    BEGIN
        INSERT INTO flow.calculation_event_outbox (
            event_id, aggregate_type, aggregate_id, event_type,
            payload_json, delivery_status, last_error
        ) VALUES (
            'v026-verification-bad-event-' || v_suffix, 'CALCULATION_VERSION',
            v_version_id, 'FAILED', '{"status":"FAILED"}'::jsonb,
            'FAILED', '   '
        );
    EXCEPTION
        WHEN check_violation THEN
            v_invalid_rejected := true;
    END;

    IF NOT v_invalid_rejected THEN
        RAISE EXCEPTION 'Expected failed Outbox event without error to be rejected';
    END IF;
END
$$;

ROLLBACK;

SELECT 'V026 calculation version, snapshot and Outbox verification passed.'
    AS verification_result;
