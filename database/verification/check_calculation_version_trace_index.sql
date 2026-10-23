-- V044 calc.calculation_version 追踪号索引探针。
-- 检查追踪号索引是否存在；探针只读，不写业务数据。
-- 执行方式：psql -v ON_ERROR_STOP=1 -f database/verification/check_calculation_version_trace_index.sql

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_indexes
         WHERE schemaname = 'calc'
           AND tablename = 'calculation_version'
           AND indexname = 'idx_calculation_version_trace'
    ) THEN
        RAISE EXCEPTION 'Missing index calc.idx_calculation_version_trace';
    END IF;
END
$$;

SELECT 'V044 calculation version trace index present.' AS verification_result;
