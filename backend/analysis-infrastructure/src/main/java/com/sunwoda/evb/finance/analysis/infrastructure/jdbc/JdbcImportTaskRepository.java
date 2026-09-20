package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportTaskRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcImportTaskRepository implements ImportTaskRepository {
    private final JdbcTemplate jdbc;

    public JdbcImportTaskRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public ImportTask save(ImportTask task) {
        if (task.getId() == null) {
            Long id = jdbc.queryForObject("INSERT INTO flow.import_task "
                    + "(task_no, batch_id, dataset_code, file_name, checksum, status, issue_count, created_by, created_at, updated_at) "
                    + "SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ? FROM meta.analysis_batch WHERE batch_no = ? RETURNING id",
                    Long.class, task.getTaskNo(), task.getDatasetCode(), task.getFileName(), task.getChecksum(),
                    task.getStatus().name(), task.getIssueCount(), task.getCreatedBy(), Timestamp.valueOf(task.getCreatedAt()),
                    Timestamp.valueOf(task.getUpdatedAt()), task.getBatchNo());
            return copyWithId(task, id);
        }
        jdbc.update("UPDATE flow.import_task SET status = ?, issue_count = ?, updated_at = ? WHERE id = ?",
                task.getStatus().name(), task.getIssueCount(), Timestamp.valueOf(task.getUpdatedAt()), task.getId());
        return task;
    }

    @Override
    public Optional<ImportTask> findByTaskNo(String taskNo) {
        return jdbc.query("SELECT t.id, t.task_no, b.batch_no, t.dataset_code, t.file_name, t.checksum, "
                        + "t.status, t.issue_count, t.created_by, t.created_at FROM flow.import_task t "
                        + "JOIN meta.analysis_batch b ON b.id = t.batch_id WHERE t.task_no = ?", rs -> {
                    if (!rs.next()) return Optional.<ImportTask>empty();
                    ImportTask task = task(rs);
                    task.moveTo(ImportStatus.valueOf(rs.getString("status")), rs.getInt("issue_count"));
                    return Optional.of(task);
                }, taskNo);
    }

    @Override
    public boolean existsByBatchAndDataset(String batchNo, String datasetCode) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM flow.import_task t "
                + "JOIN meta.analysis_batch b ON b.id = t.batch_id WHERE b.batch_no = ? AND t.dataset_code = ?",
                Integer.class, batchNo, datasetCode);
        return count != null && count > 0;
    }

    @Override
    public List<ImportTask> findByBatchNo(String batchNo) {
        List<ImportTask> result = jdbc.query("SELECT t.id, t.task_no, b.batch_no, t.dataset_code, t.file_name, t.checksum, "
                        + "t.status, t.issue_count, t.created_by, t.created_at FROM flow.import_task t "
                        + "JOIN meta.analysis_batch b ON b.id = t.batch_id WHERE b.batch_no = ? ORDER BY t.id",
                (rs, row) -> {
                    ImportTask task = task(rs);
                    task.moveTo(ImportStatus.valueOf(rs.getString("status")), rs.getInt("issue_count"));
                    return task;
                }, batchNo);
        return Collections.unmodifiableList(new ArrayList<ImportTask>(result));
    }

    private ImportTask task(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new ImportTask(rs.getLong("id"), rs.getString("task_no"), rs.getString("batch_no"),
                rs.getString("dataset_code"), rs.getString("file_name"), rs.getString("checksum"),
                rs.getString("created_by"), created == null ? LocalDateTime.now() : created.toLocalDateTime());
    }

    private ImportTask copyWithId(ImportTask task, Long id) {
        ImportTask copy = new ImportTask(id, task.getTaskNo(), task.getBatchNo(), task.getDatasetCode(),
                task.getFileName(), task.getChecksum(), task.getCreatedBy(), task.getCreatedAt());
        copy.moveTo(task.getStatus(), task.getIssueCount());
        return copy;
    }
}
