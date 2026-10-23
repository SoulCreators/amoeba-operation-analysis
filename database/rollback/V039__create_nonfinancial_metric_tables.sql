-- 回滚 V039：删除非财经指标结果表与事实表（先子后父）。
DROP TABLE IF EXISTS flow.nonfinancial_result;
DROP TABLE IF EXISTS flow.nonfinancial_fact;
