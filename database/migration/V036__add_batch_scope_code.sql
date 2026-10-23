-- V036 批次绑定数据范围（SEC-01 行级隔离安全整改）。
--
-- meta.analysis_batch 此前只有 perspective，缺少实际业务 scope，
-- 批次读取、readiness 和 quality 查询只能做“用户在该视角有可见范围”的
-- 最低门禁，无法阻止用户读取本人无权查看的基地/产品线/事业部批次。
-- 本迁移为批次增加 scope_code（缺省 ALL 以兼容既有数据），配合应用层把
-- 批次读取授权从“视角级”收紧为“范围级”。
-- 只追加列与索引，不回填、不改写既有批次；历史批次默认 ALL，由有权限的
-- 维护者按需补正 scope_code。

ALTER TABLE meta.analysis_batch
    ADD COLUMN IF NOT EXISTS scope_code VARCHAR(128) NOT NULL DEFAULT 'ALL';

CREATE INDEX IF NOT EXISTS idx_analysis_batch_scope
    ON meta.analysis_batch(perspective, scope_code, period);
