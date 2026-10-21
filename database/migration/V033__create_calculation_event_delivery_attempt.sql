-- V033 计算生命周期 Outbox 投递尝试追加式历史。
-- 本次新增：每次领取追加 BEGIN、每次成功或失败确认追加 END，历史记录只追加不更新，租约接管会产生新的 BEGIN。

CREATE TABLE IF NOT EXISTS flow.calculation_event_delivery_attempt (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL,
    attempt_no INTEGER NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    claim_token VARCHAR(128) NOT NULL,
    attempt_phase VARCHAR(16) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    lease_until TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    error_message TEXT,
    trace_id VARCHAR(128),
    calculation_version_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calculation_event_delivery_attempt_event
        FOREIGN KEY (event_id) REFERENCES flow.calculation_event_outbox(event_id),
    CONSTRAINT ck_calculation_event_delivery_attempt_no
        CHECK (attempt_no > 0),
    CONSTRAINT ck_calculation_event_delivery_attempt_worker
        CHECK (length(btrim(worker_id)) > 0),
    CONSTRAINT ck_calculation_event_delivery_attempt_token
        CHECK (length(btrim(claim_token)) > 0),
    CONSTRAINT ck_calculation_event_delivery_attempt_phase_status
        CHECK (
            (attempt_phase = 'BEGIN'
                AND delivery_status = 'IN_PROGRESS'
                AND ended_at IS NULL)
            OR (attempt_phase = 'END'
                AND delivery_status IN ('DELIVERED', 'FAILED', 'MANUAL_REQUIRED')
                AND ended_at IS NOT NULL)
        ),
    CONSTRAINT ck_calculation_event_delivery_attempt_error
        CHECK (
            delivery_status NOT IN ('FAILED', 'MANUAL_REQUIRED')
            OR length(btrim(COALESCE(error_message, ''))) > 0
        )
);

-- 本次新增：同一次尝试只能有一条 BEGIN 和一条 END，保证回写幂等且历史行不可覆盖。
CREATE UNIQUE INDEX IF NOT EXISTS uk_calculation_event_delivery_attempt_phase
    ON flow.calculation_event_delivery_attempt(event_id, attempt_no, attempt_phase);

-- 本次新增：按事件、尝试号和时间提供稳定追溯顺序，不对历史行做状态覆盖。
CREATE INDEX IF NOT EXISTS idx_calculation_event_delivery_attempt_event
    ON flow.calculation_event_delivery_attempt(event_id, attempt_no, id);

CREATE INDEX IF NOT EXISTS idx_calculation_event_delivery_attempt_created
    ON flow.calculation_event_delivery_attempt(created_at, id);
