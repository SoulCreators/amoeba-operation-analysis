-- V022 数据库状态约束硬化。
--
-- 兼容性说明：V002/V004 之前没有限制 status，已部署数据库中可能存在不在
-- Java BatchStatus/ImportStatus 枚举内的历史值；issue_count 也可能存在负数，或
-- VALID 任务存在非零问题数。本迁移使用已验证的 CHECK，不做 UPDATE/回填；如果
-- 历史数据不满足约束，执行会失败，应先单独完成数据治理后再重试。
-- 既有字段、索引及 uk_import_task_batch_dataset 幂等唯一索引均不修改。
-- 原子性验证脚本中的批次探针已同步改为合法的 DRAFT；这不放宽约束。

ALTER TABLE meta.analysis_batch
    DROP CONSTRAINT IF EXISTS ck_analysis_batch_status;

ALTER TABLE meta.analysis_batch
    ADD CONSTRAINT ck_analysis_batch_status CHECK (
        status IN (
            'DRAFT',
            'CHECKING',
            'READY_FOR_CALCULATION',
            'CALCULATING',
            'CALCULATED',
            'CONFIRMED',
            'PUBLISHED',
            'FAILED'
        )
    );

ALTER TABLE flow.import_task
    DROP CONSTRAINT IF EXISTS ck_import_task_status,
    DROP CONSTRAINT IF EXISTS ck_import_task_issue_count_nonnegative,
    DROP CONSTRAINT IF EXISTS ck_import_task_valid_issue_count;

ALTER TABLE flow.import_task
    ADD CONSTRAINT ck_import_task_status CHECK (
        status IN (
            'CREATED',
            'VALIDATING',
            'VALID',
            'INVALID',
            'CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_import_task_issue_count_nonnegative CHECK (
        issue_count >= 0
    ),
    ADD CONSTRAINT ck_import_task_valid_issue_count CHECK (
        status <> 'VALID' OR issue_count = 0
    );
