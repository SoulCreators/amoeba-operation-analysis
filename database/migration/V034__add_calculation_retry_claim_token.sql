-- V034 为计算重试租约增加不可伪造的所有权凭证。
-- 本次新增：旧租约先补齐 token，再强制非空；不修改已有重试业务状态和结果数据。

ALTER TABLE flow.calculation_retry_claim
    ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128);

-- 本次新增：兼容 V029 已存在的租约记录，迁移期间为每条记录生成稳定的过渡 token。
UPDATE flow.calculation_retry_claim
SET claim_token = 'legacy-' || md5(job_id || worker_id || created_at::text)
WHERE claim_token IS NULL;

ALTER TABLE flow.calculation_retry_claim
    ALTER COLUMN claim_token SET NOT NULL;

ALTER TABLE flow.calculation_retry_claim
    ADD CONSTRAINT ck_calculation_retry_claim_token CHECK (length(btrim(claim_token)) > 0);

-- 本次新增：token 与 job_id 绑定并建立唯一索引，防止同一租约记录出现重复凭证。
CREATE UNIQUE INDEX IF NOT EXISTS uk_calculation_retry_claim_job_token
    ON flow.calculation_retry_claim(job_id, claim_token);
