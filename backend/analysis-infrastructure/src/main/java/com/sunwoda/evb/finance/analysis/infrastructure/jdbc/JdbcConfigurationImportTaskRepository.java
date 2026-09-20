package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportTaskRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcConfigurationImportTaskRepository implements ConfigurationImportTaskRepository {
    private final JdbcTemplate jdbc;

    public JdbcConfigurationImportTaskRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public ConfigurationImportTask save(ConfigurationImportTask task) {
        if (task.getId() == null) {
            Long id = jdbc.queryForObject("INSERT INTO flow.configuration_import_task "
                    + "(task_no, config_type, file_name, checksum, status, created_by, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, task.getTaskNo(),
                    task.getType().name(), task.getFileName(), task.getChecksum(), task.getStatus().name(),
                    task.getCreatedBy(), Timestamp.valueOf(task.getCreatedAt()), Timestamp.valueOf(task.getUpdatedAt()));
            return copyWithId(task, id);
        }
        jdbc.update("UPDATE flow.configuration_import_task SET status = ?, updated_at = ? WHERE id = ?",
                task.getStatus().name(), Timestamp.valueOf(task.getUpdatedAt()), task.getId());
        return task;
    }

    @Override
    public Optional<ConfigurationImportTask> findByTaskNo(String taskNo) {
        return jdbc.query("SELECT id, task_no, config_type, file_name, checksum, status, created_by, created_at "
                        + "FROM flow.configuration_import_task WHERE task_no = ?", rs -> {
                    if (!rs.next()) return Optional.<ConfigurationImportTask>empty();
                    Timestamp created = rs.getTimestamp("created_at");
                    ConfigurationImportTask task = new ConfigurationImportTask(rs.getLong("id"),
                            rs.getString("task_no"), ConfigurationImportType.valueOf(rs.getString("config_type")),
                            rs.getString("file_name"), rs.getString("checksum"), rs.getString("created_by"),
                            created == null ? LocalDateTime.now() : created.toLocalDateTime());
                    task.moveTo(ImportStatus.valueOf(rs.getString("status")));
                    return Optional.of(task);
                }, taskNo);
    }

    private ConfigurationImportTask copyWithId(ConfigurationImportTask task, Long id) {
        ConfigurationImportTask copy = new ConfigurationImportTask(id, task.getTaskNo(), task.getType(),
                task.getFileName(), task.getChecksum(), task.getCreatedBy(), task.getCreatedAt());
        copy.moveTo(task.getStatus());
        return copy;
    }
}
