-- V036 回退：移除批次数据范围列与索引。仅供应急参考，不自动执行。

DROP INDEX IF EXISTS meta.idx_analysis_batch_scope;

ALTER TABLE meta.analysis_batch
    DROP COLUMN IF EXISTS scope_code;
