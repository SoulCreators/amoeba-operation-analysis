-- V029 计算作业重试到期任务原子领取结构验证。
-- 本次新增：验证领取表、外键、索引和唯一 job_id 约束；探针数据全部回滚。
-- 执行前提：V025、V028、V029 已按顺序执行。

DO $$
DECLARE
    v_table_oid oid;
BEGIN
    SELECT c.oid INTO v_table_oid
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = 'flow' AND c.relname = 'calculation_retry_claim'
       AND c.relkind IN ('r', 'p');
    IF v_table_oid IS NULL THEN
        RAISE EXCEPTION 'Missing V029 table flow.calculation_retry_claim';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'flow.calculation_retry_claim'::regclass
           AND conname = 'fk_calculation_retry_claim_job'
    ) THEN
        RAISE EXCEPTION 'Missing V029 retry claim foreign key';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'flow'
           AND indexname = 'idx_calculation_retry_claim_expiry'
    ) THEN
        RAISE EXCEPTION 'Missing V029 retry claim expiry index';
    END IF;
END $$;

BEGIN;
-- 本次新增：使用事务内探针验证同一 job_id 的第二次领取由唯一键/CAS 拒绝，结束时不保留测试数据。
DO $$
DECLARE
    v_suffix text := substring(md5(random()::text) from 1 for 12);
    v_job_id text := 'V029-CLAIM-' || v_suffix;
    v_request_key text := 'V029-CLAIM-REQUEST-' || v_suffix;
BEGIN
    INSERT INTO flow.calculation_job
        (job_id, request_key, batch_no, status, attempt_no, last_error, created_at)
    VALUES (v_job_id, v_request_key, 'V029-CLAIM-BATCH-' || v_suffix,
            'FAILED', 1, 'V029 claim verification', CURRENT_TIMESTAMP);
    INSERT INTO flow.calculation_retry
        (job_id, request_key, status, retry_no, last_failed_attempt_no,
         next_retry_at, last_error)
    VALUES (v_job_id, v_request_key, 'PENDING_RETRY', 1, 1,
            CURRENT_TIMESTAMP, 'V029 claim verification');
    -- 本次修正：V034 已要求租约凭证非空，校验数据补齐 claim_token，保持与应用领取 SQL 一致。
    INSERT INTO flow.calculation_retry_claim
        (job_id, worker_id, claim_token, claimed_until)
    VALUES (v_job_id, 'V029-WORKER-A', 'V029-TOKEN-A', CURRENT_TIMESTAMP + INTERVAL '5 minutes');

    INSERT INTO flow.calculation_retry_claim
        (job_id, worker_id, claim_token, claimed_until)
    VALUES (v_job_id, 'V029-WORKER-B', 'V029-TOKEN-B', CURRENT_TIMESTAMP + INTERVAL '5 minutes')
    ON CONFLICT (job_id) DO NOTHING;

    IF (SELECT worker_id FROM flow.calculation_retry_claim WHERE job_id = v_job_id)
            <> 'V029-WORKER-A' THEN
        RAISE EXCEPTION 'V029 atomic claim duplicate changed the original owner';
    END IF;
END $$;
ROLLBACK;

SELECT 'V029 calculation retry atomic claim verification passed.' AS verification_result;
