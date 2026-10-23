-- 非财经指标（电芯优率）闭环骨架：长表事实 + 配对结果两张表。
-- 事实唯一键＝期间＋基地＋拉线＋型号＋类目＋目标/实际（拉线可空，按 COALESCE 归一）；
-- 配对结果每业务键一行（去目标/实际）。比例值存原值，不隐式乘 100。
-- 目标/实际配对、差额与达成率由应用层计算，本迁移只落结构与唯一约束，不写业务数据。
CREATE TABLE IF NOT EXISTS flow.nonfinancial_fact (
    id BIGSERIAL PRIMARY KEY,
    period VARCHAR(7) NOT NULL,
    base_code VARCHAR(64) NOT NULL,
    production_line VARCHAR(64),
    cell_model VARCHAR(64) NOT NULL,
    quality_category VARCHAR(64) NOT NULL,
    target_actual VARCHAR(16) NOT NULL,
    quality_value NUMERIC(30,10) NOT NULL,
    source_row_no INTEGER NOT NULL DEFAULT 0,
    effective_file_version VARCHAR(64) NOT NULL,
    CONSTRAINT ck_nonfinancial_fact_target_actual
        CHECK (target_actual IN ('TARGET', 'ACTUAL')),
    CONSTRAINT ck_nonfinancial_fact_source_row_no CHECK (source_row_no >= 0)
);

CREATE TABLE IF NOT EXISTS flow.nonfinancial_result (
    id BIGSERIAL PRIMARY KEY,
    period VARCHAR(7) NOT NULL,
    base_code VARCHAR(64) NOT NULL,
    production_line VARCHAR(64),
    cell_model VARCHAR(64) NOT NULL,
    quality_category VARCHAR(64) NOT NULL,
    target_value NUMERIC(30,10),
    actual_value NUMERIC(30,10),
    gap NUMERIC(30,10),
    achievement_rate NUMERIC(30,10),
    pairing_status VARCHAR(16) NOT NULL,
    leader_employee_no VARCHAR(64),
    leader_conflict BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_nonfinancial_result_pairing_status
        CHECK (pairing_status IN ('BOTH', 'TARGET_ONLY', 'ACTUAL_ONLY'))
);

-- 事实唯一键：同期同业务键同一目标/实际仅一条（拉线可空按空串归一）。
CREATE UNIQUE INDEX IF NOT EXISTS uk_nonfinancial_fact_key
    ON flow.nonfinancial_fact(period, base_code, COALESCE(production_line, ''),
        cell_model, quality_category, target_actual);

-- 配对结果唯一键：同期同业务键一条结果。
CREATE UNIQUE INDEX IF NOT EXISTS uk_nonfinancial_result_key
    ON flow.nonfinancial_result(period, base_code, COALESCE(production_line, ''),
        cell_model, quality_category);

CREATE INDEX IF NOT EXISTS idx_nonfinancial_fact_period ON flow.nonfinancial_fact(period);
CREATE INDEX IF NOT EXISTS idx_nonfinancial_result_period ON flow.nonfinancial_result(period);
