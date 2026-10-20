package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.repository.ConfigurationImportIssueRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

/** PostgreSQL issue storage for configuration import tasks. */
@Repository
@Primary
@Profile("db")
public class JdbcConfigurationImportIssueRepository implements ConfigurationImportIssueRepository {
    private final JdbcTemplate jdbc;

    public JdbcConfigurationImportIssueRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public synchronized void replace(String taskNo, List<ImportIssue> issues) {
        Long taskId = jdbc.queryForObject(
                "SELECT id FROM flow.configuration_import_task WHERE task_no = ?",
                Long.class, taskNo);
        jdbc.update("DELETE FROM flow.configuration_import_issue WHERE configuration_task_id = ?", taskId);
        if (issues == null) return;
        for (ImportIssue issue : issues) {
            jdbc.update("INSERT INTO flow.configuration_import_issue "
                            + "(configuration_task_id, row_no, field_name, issue_code, issue_message, raw_value) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", taskId, issue.getRowNo(), issue.getFieldName(),
                    issue.getIssueCode(), issue.getIssueMessage(), issue.getRawValue());
        }
    }

    @Override
    public List<ImportIssue> findByTaskNo(String taskNo) {
        List<ImportIssue> result = jdbc.query(
                "SELECT i.row_no, i.field_name, i.issue_code, i.issue_message, i.raw_value "
                        + "FROM flow.configuration_import_issue i "
                        + "JOIN flow.configuration_import_task t ON t.id = i.configuration_task_id "
                        + "WHERE t.task_no = ? ORDER BY i.id",
                (rs, row) -> new ImportIssue((Integer) rs.getObject("row_no"),
                        rs.getString("field_name"), rs.getString("issue_code"),
                        rs.getString("issue_message"), rs.getString("raw_value")), taskNo);
        return result == null ? Collections.<ImportIssue>emptyList() : result;
    }
}
