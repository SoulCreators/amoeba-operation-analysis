package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportCleanupTask;
import com.sunwoda.evb.finance.analysis.application.port.ExportCleanupPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** PostgreSQL adapter used by the internal worker to clean expired exports. */
@Repository
@Profile("db")
public class JdbcExportCleanupRepository implements ExportCleanupPort {
    private static final int MAX_ATTEMPTS = 3;

    private static final String EXPIRE_DUE_SQL =
            "UPDATE flow.export_task SET status = 'EXPIRED', "
                    + "cleanup_status = CASE WHEN status IN ('SUCCEEDED', 'EXPIRED') "
                    + "AND cleanup_status = 'NOT_REQUIRED' "
                    + "THEN 'PENDING' ELSE cleanup_status END, "
                    + "cleanup_next_attempt_at = CASE WHEN status IN ('SUCCEEDED', 'EXPIRED') "
                    + "AND cleanup_status = 'NOT_REQUIRED' "
                    + "THEN ? ELSE cleanup_next_attempt_at END, "
                    + "cleanup_claim_token = NULL, cleanup_lease_until = NULL, "
                    + "updated_at = ? "
                    + "WHERE (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED') "
                    + "OR (status = 'EXPIRED' AND cleanup_status = 'NOT_REQUIRED')) "
                    + "AND expires_at <= ?";

    /**
     * Claims rows atomically. PostgreSQL's SKIP LOCKED keeps concurrent
     * internal workers from waiting on or duplicating each other's claims.
     */
    private static final String CLAIM_SQL =
            "WITH candidates AS ("
                    + " SELECT id, md5(? || ':' || random()::text || ':' "
                    + " || clock_timestamp()::text || ':' || id::text) AS claim_token"
                    + " FROM flow.export_task"
                    + " WHERE status = 'EXPIRED'"
                    + " AND cleanup_status IN ('PENDING', 'IN_PROGRESS')"
                    + " AND cleanup_attempts < " + MAX_ATTEMPTS
                    + " AND (cleanup_next_attempt_at IS NULL OR cleanup_next_attempt_at <= ?)"
                    + " AND (cleanup_lease_until IS NULL OR cleanup_lease_until <= ?)"
                    + " ORDER BY cleanup_next_attempt_at NULLS FIRST, expires_at, id"
                    + " LIMIT ? FOR UPDATE SKIP LOCKED"
                    + ")"
                    + " UPDATE flow.export_task AS task"
                    + " SET cleanup_status = 'IN_PROGRESS',"
                    + " cleanup_attempts = task.cleanup_attempts + 1,"
                    + " cleanup_claim_token = candidates.claim_token,"
                    + " cleanup_lease_until = ?, cleanup_next_attempt_at = NULL,"
                    + " updated_at = ?"
                    + " FROM candidates"
                    + " WHERE task.id = candidates.id"
                    + " RETURNING task.job_id, task.user_id, task.job_id AS file_key,"
                    + " task.cleanup_attempts, task.cleanup_claim_token, task.expires_at";

    private static final String MARK_SUCCEEDED_SQL =
            "UPDATE flow.export_task SET cleanup_status = 'DELETED', "
                    + "cleanup_claim_token = NULL, cleanup_lease_until = NULL, "
                    + "cleanup_next_attempt_at = NULL, cleanup_last_error = NULL, "
                    + "cleanup_completed_at = ?, updated_at = ? "
                    + "WHERE job_id = ? AND status = 'EXPIRED' "
                    + "AND cleanup_status = 'IN_PROGRESS' AND cleanup_claim_token = ?";

    private static final String MARK_RETRY_SQL =
            "UPDATE flow.export_task SET cleanup_status = "
                    + "CASE WHEN cleanup_attempts >= " + MAX_ATTEMPTS
                    + " OR ? THEN 'FAILED' ELSE 'PENDING' END, "
                    + "cleanup_claim_token = NULL, cleanup_lease_until = NULL, "
                    + "cleanup_next_attempt_at = CASE WHEN cleanup_attempts >= " + MAX_ATTEMPTS
                    + " OR ? THEN NULL ELSE CAST(? AS TIMESTAMP) END, cleanup_last_error = ?, "
                    + "cleanup_completed_at = NULL, updated_at = ? "
                    + "WHERE job_id = ? AND status = 'EXPIRED' "
                    + "AND cleanup_status = 'IN_PROGRESS' AND cleanup_claim_token = ?";

    private static final RowMapper<ExportCleanupTask> CLEANUP_TASK_ROW_MAPPER =
            new RowMapper<ExportCleanupTask>() {
                @Override
                public ExportCleanupTask mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    if (expiresAt == null) {
                        throw new IllegalStateException("导出任务字段为空: expires_at");
                    }
                    return new ExportCleanupTask(rs.getString("job_id"),
                            rs.getString("user_id"), rs.getString("file_key"),
                            rs.getInt("cleanup_attempts"),
                            rs.getString("cleanup_claim_token"), expiresAt.toLocalDateTime());
                }
            };

    private final JdbcTemplate jdbc;

    @Autowired
    public JdbcExportCleanupRepository(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    JdbcExportCleanupRepository(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public void expireDueTasks(LocalDateTime now) {
        LocalDateTime at = required(now, "now");
        Timestamp timestamp = timestamp(at);
        jdbc.update(EXPIRE_DUE_SQL, timestamp, timestamp, timestamp);
    }

    @Override
    public List<ExportCleanupTask> claimExpired(String workerId, int limit,
                                                LocalDateTime now,
                                                LocalDateTime leaseUntil) {
        String owner = required(workerId, "workerId");
        LocalDateTime at = required(now, "now");
        LocalDateTime lease = required(leaseUntil, "leaseUntil");
        if (lease.isBefore(at)) {
            throw new IllegalArgumentException("leaseUntil 不能早于 now");
        }
        if (limit <= 0) {
            return Collections.emptyList();
        }

        return jdbc.query(CLAIM_SQL, CLEANUP_TASK_ROW_MAPPER, owner, timestamp(at),
                timestamp(at), limit, timestamp(lease), timestamp(at));
    }

    @Override
    public void markCleanupSucceeded(String jobId, String claimToken, LocalDateTime now) {
        String key = required(jobId, "jobId");
        String token = required(claimToken, "claimToken");
        Timestamp timestamp = timestamp(required(now, "now"));
        jdbc.update(MARK_SUCCEEDED_SQL, timestamp, timestamp, key, token);
    }

    @Override
    public void markCleanupRetry(String jobId, String claimToken, String errorMessage,
                                 LocalDateTime nextAttemptAt, LocalDateTime now,
                                 boolean terminal) {
        String key = required(jobId, "jobId");
        String token = required(claimToken, "claimToken");
        String error = required(errorMessage, "errorMessage");
        LocalDateTime at = required(now, "now");
        LocalDateTime next = required(nextAttemptAt, "nextAttemptAt");
        jdbc.update(MARK_RETRY_SQL, terminal, terminal, timestamp(next), error,
                timestamp(at), key, token);
    }

    private static LocalDateTime required(LocalDateTime value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
