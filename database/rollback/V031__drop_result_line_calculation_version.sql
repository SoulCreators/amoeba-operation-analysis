-- V031 回滚：移除结果明细版本归属字段及其索引。
DROP INDEX IF EXISTS calc.idx_pnl_result_line_calculation_version;
ALTER TABLE calc.pnl_result_line
    DROP CONSTRAINT IF EXISTS fk_pnl_result_line_calculation_version;
ALTER TABLE calc.pnl_result_line
    DROP COLUMN IF EXISTS calculation_version_id;
