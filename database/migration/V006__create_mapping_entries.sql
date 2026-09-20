-- 映射关系只支持新增、更新和失效，不设计生效期间和版本字段。
CREATE TABLE IF NOT EXISTS cfg.mapping_entry (
    id BIGSERIAL PRIMARY KEY,
    mapping_type VARCHAR(64) NOT NULL,
    match_key TEXT NOT NULL,
    mapped_value TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mapping_entry UNIQUE (mapping_type, match_key)
);

CREATE INDEX IF NOT EXISTS idx_mapping_entry_type_enabled
    ON cfg.mapping_entry(mapping_type, enabled);
