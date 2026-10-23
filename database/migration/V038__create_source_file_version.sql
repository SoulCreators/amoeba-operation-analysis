-- 源文件版本治理：长期保留每个对象的历史版本，同对象至多一个生效版本。
-- 生效指针切换在事务内「先失效后生效」完成；部分唯一索引 uk_source_file_version_active_object
-- 在数据库侧兜底「同对象最多一个生效版本」。旧版本只失效不删除，物理删除仅受控保留期清理。
CREATE TABLE IF NOT EXISTS flow.source_file_version (
    id BIGSERIAL PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL UNIQUE,
    batch_no VARCHAR(64) NOT NULL,
    period VARCHAR(7) NOT NULL,
    dataset_code VARCHAR(64) NOT NULL,
    object_code VARCHAR(64) NOT NULL,
    version_no INTEGER NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(64),
    file_hash VARCHAR(128) NOT NULL,
    object_key VARCHAR(512),
    row_count BIGINT NOT NULL DEFAULT 0,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    active BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by VARCHAR(64) NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_source_file_version_row_count CHECK (row_count >= 0),
    CONSTRAINT ck_source_file_version_validation_status
        CHECK (validation_status IN ('PENDING', 'VALID', 'INVALID')),
    CONSTRAINT fk_source_file_version_batch
        FOREIGN KEY (batch_no) REFERENCES meta.analysis_batch(batch_no)
);

-- 期间＋数据对象＋版本号唯一：同一对象每个版本号至多一条。
CREATE UNIQUE INDEX IF NOT EXISTS uk_source_file_version_object_version
    ON flow.source_file_version(period, object_code, version_no);

-- 部分唯一索引：同期间＋数据对象最多一个生效版本。
CREATE UNIQUE INDEX IF NOT EXISTS uk_source_file_version_active_object
    ON flow.source_file_version(period, object_code) WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_source_file_version_batch
    ON flow.source_file_version(batch_no);
