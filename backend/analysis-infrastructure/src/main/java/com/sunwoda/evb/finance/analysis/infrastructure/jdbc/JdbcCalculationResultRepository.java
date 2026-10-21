package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** PostgreSQL结果仓储。通过db Profile启用，默认内存仓储不受影响。 */
@Repository
// 本次修改：db Profile 下优先注入 JDBC 结果仓储，避免与默认内存仓储产生歧义。
@Primary
@Profile("db")
public class JdbcCalculationResultRepository implements CalculationResultRepository {
    private final JdbcTemplate jdbc;

    public JdbcCalculationResultRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 本次修改：为结果快照覆盖保存建立Spring事务边界，确保删除旧数据与写入新数据原子提交。 */
    @Override
    @Transactional
    public synchronized CalculationResult save(CalculationResult result) {
        Long batchId = jdbc.queryForObject("SELECT id FROM meta.analysis_batch WHERE batch_no = ?",
                Long.class, result.getBatchNo());
        jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                        + "(SELECT id FROM calc.pnl_result WHERE batch_id = ?)", batchId);
        jdbc.update("DELETE FROM calc.pnl_result WHERE batch_id = ?", batchId);
        for (PnlResult pnl : result.getResults()) {
            final String sql = "INSERT INTO calc.pnl_result "
                    + "(batch_id, perspective, scope_code, result_status, version_no, calculated_at, confirmed_by, confirmed_at, "
                    + "published_by, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            Long resultId = jdbc.queryForObject(sql, Long.class, batchId,
                    result.getPerspective().getCode(), pnl.getScopeCode(), result.getStatus().name(),
                    result.getVersionNo(), timestamp(result.getCalculatedAt()), result.getConfirmedBy(), timestamp(result.getConfirmedAt()),
                    result.getPublishedBy(), timestamp(result.getPublishedAt()));
            for (Map.Entry<String, BigDecimal> line : pnl.getLines().entrySet()) {
                jdbc.update("INSERT INTO calc.pnl_result_line(result_id, line_code, line_value) VALUES (?, ?, ?)",
                        resultId, line.getKey(), line.getValue());
            }
        }
        return result;
    }

    @Override
    public Optional<CalculationResult> findByBatchNo(String batchNo) {
        List<Header> headers = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? ORDER BY p.id", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), batchNo);
        return assemble(batchNo, headers);
    }

    @Override
    public Optional<CalculationResult> findByBatchNoForUser(String batchNo, String userId) {
        List<Header> headers = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? AND EXISTS (SELECT 1 FROM iam.user_scope s "
                        + "WHERE s.user_id = ? AND s.perspective = p.perspective AND s.enabled = TRUE "
                        // 本次修改：ADMIN 仅在同一 perspective 下绕过 scope_code，仍受 enabled 门禁约束。
                        + "AND (s.role_code = 'ADMIN' OR s.scope_code = 'ALL' OR s.scope_code = p.scope_code)) "
                        + "ORDER BY p.id", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), batchNo, userId);

        if (!headers.isEmpty()) return assemble(batchNo, headers);
        return Optional.empty();
    }

    @Override
    public Optional<CalculationResult> findMetadataByBatchNo(String batchNo) {
        List<Header> metadata = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? ORDER BY p.id LIMIT 1", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), batchNo);
        if (metadata.isEmpty()) return Optional.empty();
        Header first = metadata.get(0);
        return Optional.of(new CalculationResult(batchNo, first.perspective, new ArrayList<PnlResult>(),
                first.versionNo, first.status, first.confirmedBy, first.publishedBy, first.calculatedAt,
                first.confirmedAt, first.publishedAt));
    }

    private Optional<CalculationResult> assemble(String batchNo, List<Header> headers) {
        if (headers.isEmpty()) return Optional.empty();
        List<PnlResult> results = new ArrayList<PnlResult>();
        for (Header header : headers) {
            Map<String, BigDecimal> lines = new LinkedHashMap<String, BigDecimal>();
            jdbc.query("SELECT line_code, line_value FROM calc.pnl_result_line WHERE result_id = ? ORDER BY id",
                    (RowCallbackHandler) rs -> lines.put(rs.getString("line_code"), rs.getBigDecimal("line_value")),
                    header.resultId);
            results.add(new PnlResult(header.scopeCode, lines));
        }
        Header first = headers.get(0);
        return Optional.of(new CalculationResult(batchNo, first.perspective, results, first.versionNo,
                first.status, first.confirmedBy, first.publishedBy, first.calculatedAt, first.confirmedAt, first.publishedAt));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static class Header {
        final AnalysisPerspective perspective;
        final CalculationResultStatus status;
        final String versionNo;
        final LocalDateTime calculatedAt;
        final String confirmedBy;
        final LocalDateTime confirmedAt;
        final String publishedBy;
        final LocalDateTime publishedAt;
        final String scopeCode;
        final Long resultId;

        Header(AnalysisPerspective perspective, CalculationResultStatus status, String versionNo,
               LocalDateTime calculatedAt, String confirmedBy, LocalDateTime confirmedAt, String publishedBy,
               LocalDateTime publishedAt, String scopeCode, Long resultId) {
            this.perspective = perspective;
            this.status = status;
            this.versionNo = versionNo;
            this.calculatedAt = calculatedAt;
            this.confirmedBy = confirmedBy;
            this.confirmedAt = confirmedAt;
            this.publishedBy = publishedBy;
            this.publishedAt = publishedAt;
            this.scopeCode = scopeCode;
            this.resultId = resultId;
        }
    }
}
