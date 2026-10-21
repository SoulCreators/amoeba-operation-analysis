-- V028 计算作业失败处理结构验证。
-- 本次新增：验证表、约束、request_key 幂等和人工审计字段；探针数据全部回滚。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_calculation_retry.sql

DO $$
DECLARE
    v_table_oid oid;
    v_constraint_name text;
BEGIN
    -- 本次新增：先确认两张 V028 表存在于 flow schema。
    FOREACH v_constraint_name IN ARRAY ARRAY['calculation_retry', 'calculation_retry_action'] LOOP
        SELECT c.oid INTO v_table_oid
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'flow' AND c.relname = v_constraint_name
           AND c.relkind IN ('r', 'p');
        IF v_table_oid IS NULL THEN
            RAISE EXCEPTION 'Missing V028 table flow.%', v_constraint_name;
        END IF;
    END LOOP;

    -- 本次新增：确认重试状态表的重要约束未被遗漏。
    FOREACH v_constraint_name IN ARRAY ARRAY[
        'uk_calculation_retry_job_id', 'uk_calculation_retry_request_key',
        'fk_calculation_retry_job', 'ck_calculation_retry_status',
        'ck_calculation_retry_no', 'ck_calculation_retry_schedule',
        'ck_calculation_retry_failure_detail'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint con
             WHERE con.conrelid = 'flow.calculation_retry'::regclass
               AND con.conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing V028 constraint flow.calculation_retry.%', v_constraint_name;
        END IF;
    END LOOP;

    -- 本次新增：确认人工审计约束存在，保证操作者、原因和结果不可为空白。
    FOREACH v_constraint_name IN ARRAY ARRAY[
        'fk_calculation_retry_action_job', 'ck_calculation_retry_action_type',
        'ck_calculation_retry_action_attempt_no', 'ck_calculation_retry_action_operator',
        'ck_calculation_retry_action_reason', 'ck_calculation_retry_action_result'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint con
             WHERE con.conrelid = 'flow.calculation_retry_action'::regclass
               AND con.conname = v_constraint_name
        ) THEN
            RAISE EXCEPTION 'Missing V028 constraint flow.calculation_retry_action.%', v_constraint_name;
        END IF;
    END LOOP;
END
$$;

BEGIN;

DO $$
DECLARE
    v_suffix text := substr(md5(clock_timestamp()::text || random()::text), 1, 16);
    v_job_id text := 'calculation-retry-verification-' || v_suffix;
    v_request_key text := 'calculation-retry-verification-request-' || v_suffix;
    v_duplicate_rejected boolean := false;
BEGIN
    -- 本次新增：插入最小作业、重试状态和人工动作，验证 V028 字段可以承载完整链路。
    INSERT INTO flow.calculation_job (
        job_id, request_key, batch_no, status, attempt_no, last_error
    ) VALUES (
        v_job_id, v_request_key, 'calculation-retry-verification-batch', 'FAILED', 4,
        'verification failure'
    );

    INSERT INTO flow.calculation_retry (
        job_id, request_key, status, retry_no, last_failed_attempt_no, last_error
    ) VALUES (
        v_job_id, v_request_key, 'MANUAL_REQUIRED', 3, 4, 'verification failure'
    );

    INSERT INTO flow.calculation_retry_action (
        job_id, request_key, action_type, operator, reason,
        original_attempt_no, result, result_detail
    ) VALUES (
        v_job_id, v_request_key, 'MANUAL_COMPENSATE', 'verification-operator',
        'verification reason', 4, 'COMPENSATED', 'verification result'
    );

    BEGIN
        -- 本次新增：重复 request_key 必须被数据库唯一约束拒绝，防止重复失败处理链。
        INSERT INTO flow.calculation_retry (
            job_id, request_key, status, retry_no
        ) VALUES (
            v_job_id || '-duplicate', v_request_key, 'ACTIVE', 0
        );
    EXCEPTION
        WHEN unique_violation THEN
            v_duplicate_rejected := true;
    END;

    IF NOT v_duplicate_rejected THEN
        RAISE EXCEPTION 'Expected duplicate V028 request_key to be rejected';
    END IF;
END
$$;

-- 本次新增：验证脚本不保留探针数据，便于在共享测试库重复执行。
ROLLBACK;

SELECT 'V028 calculation retry structure and idempotency verification passed.'
    AS verification_result;
