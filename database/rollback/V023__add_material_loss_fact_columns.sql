-- V023 rollback：回滚会丢失 raw.pnl_fact 新增字段中已写入的数据。
-- 执行前必须完成备份，并在变更窗口中执行。
ALTER TABLE raw.pnl_fact
    DROP COLUMN IF EXISTS material_loss,
    DROP COLUMN IF EXISTS budget_material_loss;
