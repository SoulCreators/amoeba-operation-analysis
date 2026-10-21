-- V034 回滚脚本：只移除租约 token 相关对象，不删除租约记录。

DROP INDEX IF EXISTS flow.uk_calculation_retry_claim_job_token;
ALTER TABLE flow.calculation_retry_claim
    DROP CONSTRAINT IF EXISTS ck_calculation_retry_claim_token;
ALTER TABLE flow.calculation_retry_claim
    DROP COLUMN IF EXISTS claim_token;
