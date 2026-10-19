package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.model.ExportFormat;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportFileStoragePort;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PostgreSQL/JDBC export task metadata repository.
 *
 * <p>The database stores task metadata only.  Export bytes are persisted
 * through the optional file storage port, using the task id as the stable
 * file key.  Without that port this remains a metadata-only adapter and
 * download stays unavailable.</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcExportTaskRepository implements ExportTaskPort {
    private static final String SELECT_COLUMNS =
            "job_id, user_id, request_key, file_name, content_type, status, "
                    + "created_at, updated_at, finished_at, expires_at, error_message";

    private static final String CREATE_SQL =
            "INSERT INTO flow.export_task AS existing "
                    + "(job_id, user_id, request_key, file_name, content_type, "
                    + "batch_no, result_version, perspective, result_status, format, "
                    + "summary_scopes, detail_scopes, status, created_at, updated_at, expires_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', ?, ?, ?) "
                    + "ON CONFLICT (user_id, request_key) DO UPDATE "
                    + "SET updated_at = existing.updated_at "
                    + "RETURNING " + SELECT_COLUMNS;

    private static final String FIND_SNAPSHOT_SQL =
            "SELECT batch_no, result_version, perspective, result_status, format, "
                    + "summary_scopes, detail_scopes FROM flow.export_task WHERE job_id = ?";

    private static final String FIND_BY_ID_SQL =
            "SELECT " + SELECT_COLUMNS + " FROM flow.export_task WHERE job_id = ?";

    private static final String FIND_OWNED_SQL =
            "SELECT " + SELECT_COLUMNS + " FROM flow.export_task "
                    + "WHERE job_id = ? AND user_id = ?";

    private static final String EXPIRE_SQL =
            "UPDATE flow.export_task SET status = 'EXPIRED', "
                    + "cleanup_status = CASE WHEN status = 'SUCCEEDED' "
                    + "THEN 'PENDING' ELSE cleanup_status END, "
                    + "cleanup_next_attempt_at = CASE WHEN status = 'SUCCEEDED' "
                    + "THEN ? ELSE cleanup_next_attempt_at END, updated_at = ? "
                    + "WHERE job_id = ? AND user_id = ? AND status <> 'EXPIRED' "
                    + "AND expires_at <= ?";

    private static final String MARK_RUNNING_SQL =
            "UPDATE flow.export_task SET status = 'RUNNING', updated_at = ? "
                    + "WHERE job_id = ? AND status = 'QUEUED' AND expires_at > ?";

    private static final String COMPLETE_SQL =
            "UPDATE flow.export_task SET file_name = ?, content_type = ?, "
                    + "status = 'SUCCEEDED', finished_at = ?, updated_at = ?, "
                    + "expires_at = ?, error_message = NULL "
                    + "WHERE job_id = ? AND status IN ('QUEUED', 'RUNNING') "
                    + "AND expires_at > ?";

    private static final String FAIL_SQL =
            "UPDATE flow.export_task SET status = 'FAILED', error_message = ?, "
                    + "finished_at = ?, updated_at = ? "
                    + "WHERE job_id = ? AND status NOT IN ('SUCCEEDED', 'FAILED')";

    private static final RowMapper<ExportJob> JOB_ROW_MAPPER =
            new RowMapper<ExportJob>() {
                @Override
                public ExportJob mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return mapJob(rs);
                }
            };

    private final JdbcTemplate jdbc;
    private final ExportFileStoragePort fileStorage;

    /**
     * Spring entry point.  The nullable dependency keeps the db profile safe
     * when no export file storage adapter has been configured.
     */
    @Autowired
    public JdbcExportTaskRepository(DataSource dataSource,
                                    @Nullable ExportFileStoragePort fileStorage) {
        this(new JdbcTemplate(dataSource), fileStorage);
    }

    public JdbcExportTaskRepository(DataSource dataSource) {
        this(dataSource, null);
    }

    JdbcExportTaskRepository(JdbcTemplate jdbc) {
        this(jdbc, null);
    }

    JdbcExportTaskRepository(JdbcTemplate jdbc, ExportFileStoragePort fileStorage) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
        this.fileStorage = fileStorage;
    }

    @Override
    public ExportJob create(String userId, String requestKey,
                            String fileName, String contentType) {
        return createInternal(userId, requestKey, required(fileName, "fileName"),
                defaultContentType(contentType), null);
    }

    @Override
    public ExportJob create(String userId, String requestKey,
                            ExportTaskSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot 不能为空");
        return createInternal(userId, requestKey, snapshot.fileName(),
                snapshot.contentType(), snapshot);
    }

    @Override
    public Optional<ExportTaskSnapshot> snapshot(String jobId) {
        if (isBlank(jobId)) {
            return Optional.empty();
        }
        List<ExportTaskSnapshot> rows = jdbc.query(FIND_SNAPSHOT_SQL,
                new RowMapper<ExportTaskSnapshot>() {
                    @Override
                    public ExportTaskSnapshot mapRow(ResultSet rs, int rowNum)
                            throws SQLException {
                        return mapSnapshot(rs);
                    }
                }, jobId.trim());
        return rows.isEmpty() ? Optional.<ExportTaskSnapshot>empty()
                : Optional.ofNullable(rows.get(0));
    }

    private ExportJob createInternal(String userId, String requestKey,
                                     String fileName, String contentType,
                                     final ExportTaskSnapshot snapshot) {
        String owner = required(userId, "userId");
        String key = required(requestKey, "requestKey");
        String normalizedName = required(fileName, "fileName");
        String normalizedType = defaultContentType(contentType);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(15);
        String jobId = "EXP-" + UUID.randomUUID().toString().replace("-", "");

        List<ExportJob> rows = jdbc.query(new org.springframework.jdbc.core.PreparedStatementCreator() {
            @Override
            public PreparedStatement createPreparedStatement(Connection connection)
                    throws SQLException {
                PreparedStatement statement = connection.prepareStatement(CREATE_SQL);
                int index = 1;
                statement.setString(index++, jobId);
                statement.setString(index++, owner);
                statement.setString(index++, key);
                statement.setString(index++, normalizedName);
                statement.setString(index++, normalizedType);
                if (snapshot == null) {
                    statement.setNull(index++, Types.VARCHAR);
                    statement.setNull(index++, Types.VARCHAR);
                    statement.setNull(index++, Types.VARCHAR);
                    statement.setNull(index++, Types.VARCHAR);
                    statement.setNull(index++, Types.VARCHAR);
                    statement.setNull(index++, Types.ARRAY);
                    statement.setNull(index++, Types.ARRAY);
                } else {
                    statement.setString(index++, snapshot.getBatchNo());
                    statement.setString(index++, snapshot.getResultVersion());
                    statement.setString(index++, snapshot.getPerspective().getCode());
                    statement.setString(index++, snapshot.getResultStatus().name());
                    statement.setString(index++, snapshot.getFormat().name());
                    statement.setArray(index++, connection.createArrayOf("text",
                            snapshot.getSummaryScopes().toArray(new String[0])));
                    statement.setArray(index++, connection.createArrayOf("text",
                            snapshot.getDetailScopes().toArray(new String[0])));
                }
                statement.setTimestamp(index++, timestamp(now));
                statement.setTimestamp(index++, timestamp(now));
                statement.setTimestamp(index, timestamp(expiresAt));
                return statement;
            }
        }, JOB_ROW_MAPPER);
        if (rows.isEmpty()) {
            throw new IllegalStateException("创建导出任务未返回任务记录: " + key);
        }
        return rows.get(0);
    }

    @Override
    public Optional<ExportJob> status(String jobId, String userId) {
        if (isBlank(jobId) || isBlank(userId)) {
            return Optional.empty();
        }
        ExportJob job = findOwned(jobId.trim(), userId.trim());
        if (job == null) {
            return Optional.empty();
        }
        return Optional.of(expireIfNeeded(job));
    }

    @Override
    public Optional<ExportFile> download(String jobId, String userId) {
        if (isBlank(jobId) || isBlank(userId)) {
            return Optional.empty();
        }
        ExportJob job = findOwned(jobId.trim(), userId.trim());
        if (job == null) {
            return Optional.empty();
        }
        job = expireIfNeeded(job);
        if (job.getStatus() != ExportJobStatus.SUCCEEDED) {
            return Optional.empty();
        }

        // Keep metadata-only mode non-downloadable.  A storage lookup is only
        // allowed after the ownership, lifecycle, and expiry checks above.
        if (fileStorage == null) {
            return Optional.empty();
        }
        return fileStorage.open(fileKey(job), job.getUserId());
    }

    @Override
    public void markRunning(String jobId) {
        String key = required(jobId, "jobId");
        LocalDateTime now = LocalDateTime.now();
        if (jdbc.update(MARK_RUNNING_SQL, timestamp(now), key, timestamp(now)) > 0) {
            return;
        }

        ExportJob existing = findById(key);
        if (existing == null) {
            throw new IllegalArgumentException("导出任务不存在: " + key);
        }
        if (existing.getStatus() == ExportJobStatus.RUNNING) {
            return;
        }
        throw new IllegalStateException("导出任务不可开始: " + key);
    }

    @Override
    public void complete(String jobId, String fileName, String contentType, byte[] content) {
        String key = required(jobId, "jobId");
        String normalizedName = required(fileName, "fileName");
        String normalizedType = defaultContentType(contentType);
        requireContent(content);
        LocalDateTime now = LocalDateTime.now();

        if (fileStorage != null) {
            ExportJob existing = findById(key);
            if (existing == null) {
                throw new IllegalArgumentException("导出任务不存在: " + key);
            }
            if (existing.getStatus() != ExportJobStatus.QUEUED
                    && existing.getStatus() != ExportJobStatus.RUNNING) {
                throw new IllegalStateException("导出任务不可完成: " + key);
            }
            if (existing.isExpired(now)) {
                throw new IllegalStateException("导出任务已过期: " + key);
            }

            // Persist bytes before publishing SUCCEEDED.  If the database
            // transition is rejected, the file remains unreachable because
            // download requires the persisted successful task state.
            fileStorage.save(fileKey(existing), existing.getUserId(), normalizedName,
                    normalizedType, content);
        }

        if (jdbc.update(COMPLETE_SQL, normalizedName, normalizedType, timestamp(now),
                timestamp(now), timestamp(now.plusHours(24)), key, timestamp(now)) > 0) {
            return;
        }

        ExportJob existing = findById(key);
        if (existing == null) {
            throw new IllegalArgumentException("导出任务不存在: " + key);
        }
        throw new IllegalStateException("导出任务不可完成: " + key);
    }

    @Override
    public void fail(String jobId, String errorMessage) {
        String key = required(jobId, "jobId");
        String message = required(errorMessage, "errorMessage");
        ExportJob existing = findById(key);
        if (existing == null) {
            throw new IllegalArgumentException("导出任务不存在: " + key);
        }
        if (existing.getStatus() == ExportJobStatus.SUCCEEDED) {
            throw new IllegalStateException("已完成的导出任务不可失败: " + key);
        }
        if (existing.getStatus() == ExportJobStatus.FAILED) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (jdbc.update(FAIL_SQL, message, timestamp(now), timestamp(now), key) > 0) {
            return;
        }

        // Re-read after a lost conditional update so a concurrent completion
        // is reported as an invalid transition rather than silently ignored.
        ExportJob current = findById(key);
        if (current != null && current.getStatus() == ExportJobStatus.FAILED) {
            return;
        }
        throw new IllegalStateException("导出任务不可失败: " + key);
    }

    private ExportJob expireIfNeeded(ExportJob job) {
        LocalDateTime now = LocalDateTime.now();
        if (!job.isExpired(now)) {
            return job;
        }

        int updated = jdbc.update(EXPIRE_SQL, timestamp(now), timestamp(now), job.getJobId(),
                job.getUserId(), timestamp(now));
        if (updated > 0) {
            return job.expired(now);
        }

        ExportJob current = findOwned(job.getJobId(), job.getUserId());
        return current == null ? job : current;
    }

    private ExportJob findById(String jobId) {
        List<ExportJob> rows = jdbc.query(FIND_BY_ID_SQL, JOB_ROW_MAPPER, jobId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ExportJob findOwned(String jobId, String userId) {
        List<ExportJob> rows = jdbc.query(FIND_OWNED_SQL, JOB_ROW_MAPPER, jobId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The schema has no file_key column; the task id is the durable file key. */
    private static String fileKey(ExportJob job) {
        return job.getJobId();
    }

    private static ExportJob mapJob(ResultSet rs) throws SQLException {
        String jobId = rs.getString("job_id");
        String userId = rs.getString("user_id");
        String requestKey = rs.getString("request_key");
        String fileName = rs.getString("file_name");
        String contentType = rs.getString("content_type");
        LocalDateTime createdAt = localDateTime(rs.getTimestamp("created_at"), "created_at");
        LocalDateTime updatedAt = localDateTime(rs.getTimestamp("updated_at"), "updated_at");
        LocalDateTime finishedAt = localDateTimeOrNull(rs.getTimestamp("finished_at"));
        LocalDateTime expiresAt = localDateTime(rs.getTimestamp("expires_at"), "expires_at");
        String errorMessage = rs.getString("error_message");
        ExportJobStatus status = ExportJobStatus.valueOf(rs.getString("status"));

        if (status == ExportJobStatus.FAILED && isBlank(errorMessage)) {
            throw new IllegalStateException("FAILED 导出任务缺少 errorMessage");
        }
        return ExportJob.restore(jobId, userId, requestKey, fileName, contentType,
                createdAt, updatedAt, finishedAt, expiresAt, status, errorMessage);
    }

    private static ExportTaskSnapshot mapSnapshot(ResultSet rs) throws SQLException {
        String batchNo = rs.getString("batch_no");
        String resultVersion = rs.getString("result_version");
        String perspective = rs.getString("perspective");
        String resultStatus = rs.getString("result_status");
        String format = rs.getString("format");
        if (isBlank(batchNo) || isBlank(resultVersion) || isBlank(perspective)
                || isBlank(resultStatus) || isBlank(format)) {
            return null;
        }
        return new ExportTaskSnapshot(batchNo, resultVersion,
                com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective
                        .fromCode(perspective),
                com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus
                        .valueOf(resultStatus),
                ExportFormat.fromCode(format),
                arrayScopes(rs.getArray("summary_scopes")),
                arrayScopes(rs.getArray("detail_scopes")));
    }

    private static Set<String> arrayScopes(Array value) throws SQLException {
        if (value == null) {
            return Collections.emptySet();
        }
        Object raw = value.getArray();
        LinkedHashSet<String> scopes = new LinkedHashSet<String>();
        if (raw instanceof Object[]) {
            for (Object item : (Object[]) raw) {
                if (item != null && !isBlank(item.toString())) {
                    scopes.add(item.toString());
                }
            }
        }
        return scopes;
    }

    private static LocalDateTime localDateTime(Timestamp value, String field) {
        if (value == null) {
            throw new IllegalStateException("导出任务字段为空: " + field);
        }
        return value.toLocalDateTime();
    }

    private static LocalDateTime localDateTimeOrNull(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static String defaultContentType(String contentType) {
        return isBlank(contentType) ? "application/octet-stream" : contentType.trim();
    }

    private static void requireContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
