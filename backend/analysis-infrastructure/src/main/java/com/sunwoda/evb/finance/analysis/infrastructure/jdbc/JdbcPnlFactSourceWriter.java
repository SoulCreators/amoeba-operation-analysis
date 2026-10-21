package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.domain.repository.PnlFactSourceWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** JDBC implementation of the raw P&amp;L fact provenance replacement contract. */
@Repository
@Profile("db")
public class JdbcPnlFactSourceWriter implements PnlFactSourceWriter {
    private static final String FIND_BATCH_ID_SQL =
            "SELECT id FROM meta.analysis_batch WHERE batch_no = ?";
    private static final String DELETE_SQL =
            "DELETE FROM raw.pnl_fact_trace "
                    + "WHERE batch_id = ? AND perspective = ? AND source_task_no = ?";
    private static final String INSERT_SQL =
            "INSERT INTO raw.pnl_fact_trace(batch_id, perspective, source_task_no, "
                    + "source_file_name, source_checksum, source_sheet_name, source_row_no, "
                    + "scope_code, base_code, product_code, line_code) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String QUERY_SQL =
            "SELECT t.id, t.source_task_no, t.source_file_name, t.source_checksum, "
                    + "t.source_sheet_name, t.source_row_no, t.scope_code, t.base_code, "
                    + "t.product_code, t.line_code "
                    + "FROM raw.pnl_fact_trace t "
                    + "JOIN meta.analysis_batch b ON b.id = t.batch_id "
                    + "WHERE b.batch_no = ? AND t.perspective = ? AND t.source_task_no = ? "
                    + "ORDER BY t.id, t.source_sheet_name, t.source_row_no";

    private final JdbcTemplate jdbc;

    // 本次修复：明确指定生产 DataSource 构造器，避免存在测试构造器时 Spring 退回寻找无参构造器。
    @Autowired
    public JdbcPnlFactSourceWriter(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    JdbcPnlFactSourceWriter(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public void replace(AnalysisBatch batch, ImportTask task, List<PnlFactSource> sources) {
        validateReplaceArguments(batch, task, sources);

        Long batchId = jdbc.queryForObject(FIND_BATCH_ID_SQL, Long.class, batch.getBatchNo());
        if (batchId == null) {
            throw new IllegalStateException("批次不存在: " + batch.getBatchNo());
        }

        String perspective = batch.getPerspective().getCode();
        jdbc.update(DELETE_SQL, batchId, perspective, task.getTaskNo());
        if (sources.isEmpty()) {
            return;
        }

        jdbc.batchUpdate(INSERT_SQL, sources, sources.size(),
                new ParameterizedPreparedStatementSetter<PnlFactSource>() {
                    @Override
                    public void setValues(PreparedStatement ps, PnlFactSource source)
                            throws SQLException {
                        ps.setLong(1, batchId);
                        ps.setString(2, perspective);
                        ps.setString(3, task.getTaskNo());
                        ps.setString(4, source.getFileName());
                        setNullableString(ps, 5, source.getChecksum());
                        ps.setString(6, source.getSheetName());
                        ps.setInt(7, source.getRowNo());
                        ps.setString(8, source.getScopeCode());
                        setNullableString(ps, 9, source.getBaseCode());
                        setNullableString(ps, 10, source.getProductCode());
                        setNullableString(ps, 11, source.getLineCode());
                    }
                });
    }

    @Override
    public List<PnlFactSource> findByBatchNoAndPerspectiveAndTaskNo(
            String batchNo, AnalysisPerspective perspective, String taskNo) {
        String requiredBatchNo = required(batchNo, "batchNo");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective must not be null");
        }
        String requiredTaskNo = required(taskNo, "taskNo");
        return jdbc.query(QUERY_SQL,
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
                requiredBatchNo, perspective.getCode(), requiredTaskNo);
    }

    private static void validateReplaceArguments(AnalysisBatch batch, ImportTask task,
                                                   List<PnlFactSource> sources) {
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (sources == null) {
            throw new IllegalArgumentException("sources must not be null");
        }
        String batchNo = required(batch.getBatchNo(), "batch.batchNo");
        String taskBatchNo = required(task.getBatchNo(), "task.batchNo");
        String taskNo = required(task.getTaskNo(), "task.taskNo");
        if (batch.getPerspective() == null) {
            throw new IllegalArgumentException("batch.perspective must not be null");
        }
        if (!batchNo.equals(taskBatchNo)) {
            throw new IllegalArgumentException("task.batchNo must match batch.batchNo");
        }

        Set<String> rowKeys = new HashSet<>();
        for (PnlFactSource source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("sources must not contain null");
            }
            if (!taskNo.equals(source.getTaskNo())) {
                throw new IllegalArgumentException("source.taskNo must match task.taskNo");
            }
            String rowKey = source.getSheetName() + "\u0000" + source.getRowNo();
            if (!rowKeys.add(rowKey)) {
                throw new IllegalArgumentException(
                        "duplicate source sheet and row: " + source.getSheetName()
                                + ":" + source.getRowNo());
            }
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static void setNullableString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
