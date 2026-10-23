-- 回滚 V043：删除调整单表与索引。
DROP INDEX IF EXISTS calc.idx_adjustment_period_status;
DROP TABLE IF EXISTS calc.adjustment;
