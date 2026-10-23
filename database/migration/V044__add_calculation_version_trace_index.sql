-- V044 计算版本追踪号索引。
-- 追踪号是计算血缘的稳定查询入口，历史上游配置重放（FR-AUD-001）按追踪号
-- 定位版本。本迁移只新增索引，不写业务数据，不改变既有约束与权限。
CREATE INDEX IF NOT EXISTS idx_calculation_version_trace
    ON calc.calculation_version(trace_id);
