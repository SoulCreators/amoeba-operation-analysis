package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ExceptionWorkItemRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 异常工作台工作项 JDBC 持久化适配器，按任务整期替换、按主键单条更新。 */
@Repository
@Primary
@Profile("db")
public class JdbcExceptionWorkItemRepository implements ExceptionWorkItemRepository {
    private static final String SELECT_COLUMNS =
            "id, task_no, object_code, issue_code, severity, status, row_no, field_name, "
                    + "issue_message, raw_value, ignored_by, ignored_at, ignore_reason";

    private final JdbcTemplate jdbc;

    public JdbcExceptionWorkItemRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public synchronized List<ExceptionWorkItem> replaceByTaskNo(String taskNo, List<ExceptionWorkItem> items) {
        jdbc.update("DELETE FROM flow.exception_work_item WHERE task_no = ?", taskNo);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExceptionWorkItem> result = new ArrayList<ExceptionWorkItem>(items.size());
        for (ExceptionWorkItem item : items) {
            Long id = jdbc.queryForObject(
                    "INSERT INTO flow.exception_work_item(task_no, object_code, issue_code, "
                            + "severity, status, row_no, field_name, issue_message, raw_value, "
                            + "ignored_by, ignored_at, ignore_reason) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class,
                    item.getTaskNo(), item.getObjectCode(), item.getIssueCode(),
                    item.getSeverity().name(), item.getStatus().name(),
                    item.getRowNo(), item.getFieldName(), item.getIssueMessage(), item.getRawValue(),
                    item.getIgnoredBy(), toTimestamp(item.getIgnoredAt()), item.getIgnoreReason());
            result.add(item.withId(id));
        }
        return result;
    }

    @Override
    public List<ExceptionWorkItem> findByTaskNo(String taskNo) {
        List<ExceptionWorkItem> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM flow.exception_work_item "
                        + "WHERE task_no = ? ORDER BY id",
                (rs, row) -> map(rs), taskNo);
        return result == null ? Collections.<ExceptionWorkItem>emptyList() : result;
    }

    @Override
    public Optional<ExceptionWorkItem> findById(Long id) {
        List<ExceptionWorkItem> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM flow.exception_work_item WHERE id = ?",
                (rs, row) -> map(rs), id);
        return result.isEmpty() ? Optional.<ExceptionWorkItem>empty() : Optional.of(result.get(0));
    }

    @Override
    public ExceptionWorkItem update(ExceptionWorkItem item) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("update 需要已分配主键的工作项");
        }
        jdbc.update("UPDATE flow.exception_work_item SET severity = ?, status = ?, "
                        + "ignored_by = ?, ignored_at = ?, ignore_reason = ? WHERE id = ?",
                item.getSeverity().name(), item.getStatus().name(),
                item.getIgnoredBy(), toTimestamp(item.getIgnoredAt()),
                item.getIgnoreReason(), item.getId());
        return item;
    }

    private ExceptionWorkItem map(ResultSet rs) throws SQLException {
        Timestamp ignoredAt = rs.getTimestamp("ignored_at");
        LocalDateTime ignored = ignoredAt == null ? null : ignoredAt.toLocalDateTime();
        return ExceptionWorkItem.restore(
                rs.getLong("id"), rs.getString("task_no"), rs.getString("object_code"),
                rs.getString("issue_code"), IssueSeverity.valueOf(rs.getString("severity")),
                IssueStatus.valueOf(rs.getString("status")),
                (Integer) rs.getObject("row_no"), rs.getString("field_name"),
                rs.getString("issue_message"), rs.getString("raw_value"),
                rs.getString("ignored_by"), ignored, rs.getString("ignore_reason"));
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
