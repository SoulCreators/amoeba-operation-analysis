-- V032 计算生命周期 Outbox 原子领取租约。
-- 本次新增：以独立协调表保存 Worker、claim token 和租约到期时间，支持 PostgreSQL SKIP LOCKED、租约恢复和 CAS 确认。
-- 本迁移不改写既有 Outbox 业务数据，不改变事件正文和原有投递状态约束。

CREATE TABLE IF NOT EXISTS flow.calculation_event_outbox_claim (
    event_id VARCHAR(128) PRIMARY KEY,
    worker_id VARCHAR(128) NOT NULL,
    claim_token VARCHAR(128) NOT NULL,
    claimed_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calculation_event_outbox_claim_event
        FOREIGN KEY (event_id) REFERENCES flow.calculation_event_outbox(event_id) ON DELETE CASCADE,
    CONSTRAINT uk_calculation_event_outbox_claim_token UNIQUE (claim_token),
    CONSTRAINT ck_calculation_event_outbox_claim_worker CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT ck_calculation_event_outbox_claim_token CHECK (length(btrim(claim_token)) > 0),
    CONSTRAINT ck_calculation_event_outbox_claim_lease CHECK (claimed_until >= created_at)
);

-- 本次新增：按租约到期时间建立接管查询索引，过期租约可被新的 Worker 安全领取。
CREATE INDEX IF NOT EXISTS idx_calculation_event_outbox_claim_expiry
    ON flow.calculation_event_outbox_claim(claimed_until, event_id);
