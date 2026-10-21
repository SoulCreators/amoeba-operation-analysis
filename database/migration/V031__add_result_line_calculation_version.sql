-- V031 结果明细版本归属补齐。
-- 本次修改：为 pnl_result_line 增加与结果头一致的 calculation_version_id，
-- 使人工修订复制出的结果明细可以和历史版本一一对应，旧遗留明细继续允许为空。

ALTER TABLE calc.pnl_result_line
    ADD COLUMN IF NOT EXISTS calculation_version_id VARCHAR(128);

-- 本次修改：对已有结果头执行安全回填，不改变 V026 定义的遗留 NULL 语义。
UPDATE calc.pnl_result_line line
   SET calculation_version_id = result.calculation_version_id
  FROM calc.pnl_result result
 WHERE result.id = line.result_id
   AND line.calculation_version_id IS NULL
   AND result.calculation_version_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'calc.pnl_result_line'::regclass
           AND conname = 'fk_pnl_result_line_calculation_version'
    ) THEN
        ALTER TABLE calc.pnl_result_line
            ADD CONSTRAINT fk_pnl_result_line_calculation_version
            FOREIGN KEY (calculation_version_id)
            REFERENCES calc.calculation_version(calculation_version_id);
    END IF;
END
$$;

-- 本次修改：按版本读取结果明细时使用专用索引，旧 NULL 明细仍可按结果头读取。
CREATE INDEX IF NOT EXISTS idx_pnl_result_line_calculation_version
    ON calc.pnl_result_line(calculation_version_id, result_id, line_code)
    WHERE calculation_version_id IS NOT NULL;
