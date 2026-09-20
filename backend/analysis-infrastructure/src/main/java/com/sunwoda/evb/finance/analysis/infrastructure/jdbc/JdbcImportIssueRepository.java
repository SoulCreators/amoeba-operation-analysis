package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;

@Repository
@Primary
@Profile("db")
public class JdbcImportIssueRepository implements ImportIssueRepository {
    private final JdbcTemplate jdbc;

    public JdbcImportIssueRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public synchronized void replace(String taskNo, List<ImportIssue> issues) {
        Long taskId = jdbc.queryForObject("SELECT id FROM flow.import_task WHERE task_no = ?", Long.class, taskNo);
        jdbc.update("DELETE FROM flow.import_issue WHERE task_id = ?", taskId);
        if (issues == null) return;
        for (ImportIssue issue : issues) {
            jdbc.update("INSERT INTO flow.import_issue(task_id, row_no, field_name, issue_code, issue_message, raw_value) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", taskId, issue.getRowNo(), issue.getFieldName(),
                    issue.getIssueCode(), issue.getIssueMessage(), issue.getRawValue());
        }
    }

    @Override
    public List<ImportIssue> findByTaskNo(String taskNo) {
        List<ImportIssue> result = jdbc.query("SELECT i.row_no, i.field_name, i.issue_code, i.issue_message, i.raw_value "
                        + "FROM flow.import_issue i JOIN flow.import_task t ON t.id = i.task_id "
                        + "WHERE t.task_no = ? ORDER BY i.id", (rs, row) -> new ImportIssue(
                        (Integer) rs.getObject("row_no"), rs.getString("field_name"), rs.getString("issue_code"),
                        rs.getString("issue_message"), rs.getString("raw_value")), taskNo);
        return result == null ? Collections.<ImportIssue>emptyList() : result;
    }
}
