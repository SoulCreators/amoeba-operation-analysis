-- V037 手工回滚：删除阶段日志表及索引，并移除计算作业新增的阶段/心跳/错误码/追踪号/计数列。
-- 本次修改：回滚前必须确认没有应用适配器或后续迁移依赖这些对象；删除会清除阶段日志记录。
DROP INDEX IF EXISTS flow.idx_calculation_stage_log_job_id;
DROP TABLE IF EXISTS flow.calculation_stage_log;

ALTER TABLE flow.calculation_job DROP CONSTRAINT IF EXISTS ck_calculation_job_stage;
ALTER TABLE flow.calculation_job DROP CONSTRAINT IF EXISTS ck_calculation_job_processed_rows;
ALTER TABLE flow.calculation_job DROP CONSTRAINT IF EXISTS ck_calculation_job_issue_count;
ALTER TABLE flow.calculation_job
    DROP COLUMN IF EXISTS stage,
    DROP COLUMN IF EXISTS heartbeat_at,
    DROP COLUMN IF EXISTS error_code,
    DROP COLUMN IF EXISTS trace_id,
    DROP COLUMN IF EXISTS processed_rows,
    DROP COLUMN IF EXISTS issue_count;
