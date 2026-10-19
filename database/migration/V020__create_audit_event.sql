-- V020 audit foundation contract. Business-specific audit vocabulary is intentionally not encoded here.
CREATE TABLE IF NOT EXISTS audit.audit_event (
    event_id VARCHAR(128) PRIMARY KEY,
    trace_id VARCHAR(128),
    operator VARCHAR(128) NOT NULL,
    service VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    object_type VARCHAR(128) NOT NULL,
    object_id VARCHAR(255) NOT NULL,
    from_state VARCHAR(128),
    to_state VARCHAR(128),
    result VARCHAR(64) NOT NULL,
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_audit_event_metadata_object CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_audit_event_occurred_at
    ON audit.audit_event(occurred_at, event_id);

CREATE INDEX IF NOT EXISTS idx_audit_event_trace
    ON audit.audit_event(trace_id, occurred_at)
    WHERE trace_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_event_object
    ON audit.audit_event(object_type, object_id, occurred_at);
