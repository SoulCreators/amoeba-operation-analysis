-- V029 计算作业重试到期任务的数据库级原子领取租约。
-- 本次新增：使用唯一 job_id、租约到期时间和 Worker 标识支撑 SKIP LOCKED + CAS 领取，避免多 Worker 重复执行。
-- 本迁移只新增领取协调表和索引，不改写 V025/V028 已有业务记录。

CREATE TABLE IF NOT EXISTS flow.calculation_retry_claim (
    job_id VARCHAR(128) PRIMARY KEY,
    worker_id VARCHAR(128) NOT NULL,
    claimed_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calculation_retry_claim_job
        FOREIGN KEY (job_id) REFERENCES flow.calculation_retry(job_id) ON DELETE CASCADE,
    CONSTRAINT ck_calculation_retry_claim_worker CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT ck_calculation_retry_claim_lease CHECK (claimed_until >= created_at)
);

-- 本次新增：按租约到期时间清理/重用遗留领取记录，支持 Worker 崩溃后的安全接管。
CREATE INDEX IF NOT EXISTS idx_calculation_retry_claim_expiry
    ON flow.calculation_retry_claim(claimed_until, job_id);
