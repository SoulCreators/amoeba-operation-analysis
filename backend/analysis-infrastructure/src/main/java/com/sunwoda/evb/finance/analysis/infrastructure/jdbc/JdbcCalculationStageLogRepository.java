package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageLog;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStageStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationStageLogRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 计算作业阶段日志 JDBC 持久化适配器。
 *
 * <p>本次修改按 (job_id, stage) 幂等覆盖，按作业标识按插入顺序返回阶段日志，
 * 金额控制数使用 NUMERIC 精确映射为 BigDecimal。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationStageLogRepository implements CalculationStageLogRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO flow.calculation_stage_log "
                    + "(job_id, stage, status, started_at, finished_at, processed_rows, "
                    + "control_amount, issue_count, error_code, trace_id, message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (job_id, stage) DO UPDATE SET "
                    + "status = EXCLUDED.status, started_at = EXCLUDED.started_at, "
                    + "finished_at = EXCLUDED.finished_at, processed_rows = EXCLUDED.processed_rows, "
                    + "control_amount = EXCLUDED.control_amount, issue_count = EXCLUDED.issue_count, "
                    + "error_code = EXCLUDED.error_code, trace_id = EXCLUDED.trace_id, "
                    + "message = EXCLUDED.message";

    private static final String FIND_BY_JOB_ID_SQL =
            "SELECT id, job_id, stage, status, started_at, finished_at, processed_rows, "
                    + "control_amount, issue_count, error_code, trace_id, message "
                    + "FROM flow.calculation_stage_log WHERE job_id = ? ORDER BY id";

    private final JdbcTemplate jdbc;

    public JdbcCalculationStageLogRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public CalculationStageLog save(CalculationStageLog log) {
        if (log == null) {
            throw new IllegalArgumentException("log 不能为空");
        }
        jdbc.update(UPSERT_SQL, log.getJobId(), log.getStage().name(), log.getStatus().name(),
                timestamp(log.getStartedAt()), timestamp(log.getFinishedAt()),
                log.getProcessedRows(), log.getControlAmount(), log.getIssueCount(),
                log.getErrorCode(), log.getTraceId(), log.getMessage());
        return log;
    }

    @Override
    public List<CalculationStageLog> findByJobId(String jobId) {
        return jdbc.query(FIND_BY_JOB_ID_SQL, (rs, rowNum) -> map(rs),
                requiredText(jobId, "jobId"));
    }

    private CalculationStageLog map(ResultSet rs) throws SQLException {
        return CalculationStageLog.restore(
                rs.getLong("id"),
                rs.getString("job_id"),
                CalculationStage.valueOf(rs.getString("stage")),
                CalculationStageStatus.valueOf(rs.getString("status")),
                localDateTime(rs.getTimestamp("started_at")),
                localDateTime(rs.getTimestamp("finished_at")),
                rs.getLong("processed_rows"),
                rs.getBigDecimal("control_amount"),
                rs.getInt("issue_count"),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                rs.getString("message"));
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
