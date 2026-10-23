package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationJob;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationJobStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationStage;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 计算作业 JDBC 持久化适配器。
 *
 * <p>本次修改只覆盖当前 {@link CalculationJob} 字段往返和活动 requestKey 查询，
 * 不接入计算服务、Worker、调度器或批次/结果事务。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcCalculationJobRepository implements CalculationJobRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO flow.calculation_job "
                    + "(job_id, request_key, batch_no, status, attempt_no, last_error, "
                    + "created_at, started_at, finished_at, stage, heartbeat_at, error_code, "
                    + "trace_id, processed_rows, issue_count) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (job_id) DO UPDATE SET request_key = EXCLUDED.request_key, "
                    + "batch_no = EXCLUDED.batch_no, status = EXCLUDED.status, "
                    + "attempt_no = EXCLUDED.attempt_no, last_error = EXCLUDED.last_error, "
                    + "created_at = EXCLUDED.created_at, started_at = EXCLUDED.started_at, "
                    + "finished_at = EXCLUDED.finished_at, stage = EXCLUDED.stage, "
                    + "heartbeat_at = EXCLUDED.heartbeat_at, error_code = EXCLUDED.error_code, "
                    + "trace_id = EXCLUDED.trace_id, processed_rows = EXCLUDED.processed_rows, "
                    + "issue_count = EXCLUDED.issue_count";

    private static final String FIND_BY_JOB_ID_SQL =
            "SELECT job_id, request_key, batch_no, status, attempt_no, last_error, "
                    + "created_at, started_at, finished_at, stage, heartbeat_at, error_code, "
                    + "trace_id, processed_rows, issue_count FROM flow.calculation_job WHERE job_id = ?";

    private static final String FIND_ACTIVE_BY_REQUEST_KEY_SQL =
            "SELECT job_id, request_key, batch_no, status, attempt_no, last_error, "
                    + "created_at, started_at, finished_at, stage, heartbeat_at, error_code, "
                    + "trace_id, processed_rows, issue_count FROM flow.calculation_job "
                    + "WHERE request_key = ? AND status IN ('QUEUED', 'RUNNING') "
                    + "ORDER BY created_at, job_id LIMIT 1";

    private final JdbcTemplate jdbc;

    public JdbcCalculationJobRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public CalculationJob save(CalculationJob job) {
        if (job == null) {
            throw new IllegalArgumentException("job 不能为空");
        }
        // 本次修改：按 jobId 持久化全部现有字段与阶段进度/心跳字段，保留状态恢复所需的时间和错误信息。
        jdbc.update(UPSERT_SQL, job.getJobId(), job.getRequestKey(), job.getBatchNo(),
                job.getStatus().name(), job.getAttemptNo(), job.getLastError(),
                timestamp(job.getCreatedAt()), timestamp(job.getStartedAt()), timestamp(job.getFinishedAt()),
                stageName(job), timestamp(job.getHeartbeatAt()), job.getErrorCode(),
                job.getTraceId(), job.getProcessedRows(), job.getIssueCount());
        return job;
    }

    @Override
    public Optional<CalculationJob> findByJobId(String jobId) {
        return queryOne(FIND_BY_JOB_ID_SQL, requiredText(jobId, "jobId"));
    }

    @Override
    public Optional<CalculationJob> findActiveByRequestKey(String requestKey) {
        // 本次修改：活动查询只按当前生命周期中的 QUEUED/RUNNING 过滤，不定义跨用户作用域。
        return queryOne(FIND_ACTIVE_BY_REQUEST_KEY_SQL, requiredText(requestKey, "requestKey"));
    }

    private Optional<CalculationJob> queryOne(String sql, String value) {
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(map(rs));
        }, value);
    }

    private CalculationJob map(ResultSet rs) throws SQLException {
        return CalculationJob.restore(
                rs.getString("job_id"),
                rs.getString("request_key"),
                rs.getString("batch_no"),
                localDateTime(rs.getTimestamp("created_at")),
                CalculationJobStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_no"),
                rs.getString("last_error"),
                localDateTime(rs.getTimestamp("started_at")),
                localDateTime(rs.getTimestamp("finished_at")),
                stage(rs.getString("stage")),
                localDateTime(rs.getTimestamp("heartbeat_at")),
                rs.getString("error_code"),
                rs.getString("trace_id"),
                rs.getLong("processed_rows"),
                rs.getInt("issue_count"));
    }

    private static String stageName(CalculationJob job) {
        return job.getStage() == null ? null : job.getStage().name();
    }

    private static CalculationStage stage(String value) {
        return value == null ? null : CalculationStage.valueOf(value);
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
