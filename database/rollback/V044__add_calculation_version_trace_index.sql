-- 回滚 V044：删除计算版本追踪号索引。
DROP INDEX IF EXISTS calc.idx_calculation_version_trace;
