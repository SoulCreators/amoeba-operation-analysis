package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactTraceQuery;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/**
 * JDBC adapter for the application-side, user-scoped P&amp;L source query port.
 *
 * <p>The source rows are filtered before they leave the database.  In
 * particular, the user scope is an {@code EXISTS} predicate rather than an
 * application-side post-filter, so an unauthorized trace row is never
 * materialized into the application.</p>
 */
@Repository
@Profile("db")
public class JdbcPnlFactSourceProvider implements PnlFactTraceQuery {
    private static final String FIND_FOR_USER_SQL =
            "SELECT t.source_task_no, t.source_file_name, t.source_checksum, "
                    + "t.source_sheet_name, t.source_row_no, t.scope_code, "
                    + "t.base_code, t.product_code, t.line_code "
                    + "FROM raw.pnl_fact_trace t "
                    + "JOIN meta.analysis_batch b ON b.id = t.batch_id "
                    + "WHERE b.batch_no = ? "
                    + "AND t.perspective = ? "
                    + "AND t.source_task_no = ? "
                    + "AND EXISTS (SELECT 1 FROM iam.user_scope s "
                    + "WHERE s.user_id = ? "
                    + "AND s.enabled = TRUE "
                    // 本次修改：ADMIN 已被 Owner 授权查看全部追溯信息，跨视角并绕过普通明细门禁；普通角色保持原规则。
                    + "AND (UPPER(s.role_code) = 'ADMIN' OR "
                    + "(s.perspective = t.perspective AND s.can_view_detail = TRUE "
                    + "AND (s.scope_code = 'ALL' OR s.scope_code = t.scope_code)))) "
                    + "ORDER BY t.source_sheet_name, t.source_row_no, t.id";

    private final JdbcTemplate jdbc;

    public JdbcPnlFactSourceProvider(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    JdbcPnlFactSourceProvider(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
    }

    /**
     * Finds trace rows visible to the supplied user for one batch, perspective
     * and import task.  The method signature is kept aligned with the
     * application query port that consumes this adapter.
     */
    @Override
    public List<PnlFactSource> findForUser(String batchNo, AnalysisPerspective perspective,
                                           String taskNo, String userId) {
        String requiredBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective must not be null");
        }
        String requiredTaskNo = required(taskNo, "taskNo");
        String requiredUserId = required(userId, "userId");

        return jdbc.query(FIND_FOR_USER_SQL,
                (rs, rowNum) -> new PnlFactSource(
                        rs.getString("source_task_no"),
                        rs.getString("source_file_name"),
                        rs.getString("source_checksum"),
                        rs.getString("source_sheet_name"),
                        rs.getInt("source_row_no"),
                        rs.getString("scope_code"),
                        rs.getString("base_code"),
                        rs.getString("product_code"),
                        rs.getString("line_code")),
                requiredBatchNo, perspective.getCode(), requiredTaskNo, requiredUserId);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
