package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
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
        String calculationVersionId = findCalculationVersionId(batchId, result);
        if (calculationVersionId != null && isOrdinaryVersion(calculationVersionId)
                && jdbc.queryForObject("SELECT count(*) FROM calc.pnl_result WHERE calculation_version_id = ?",
                        Integer.class, calculationVersionId) > 0) {
            updateLifecycleOnly(result, calculationVersionId);
            return result;
        }
        // 本次修改：有版本头时只替换该版本的结果；没有版本头时只清理遗留 NULL 版本，避免误删同批次历史版本。
        if (calculationVersionId == null) {
            deleteResultDetails(batchId, null);
        } else {
            deleteResultDetails(batchId, calculationVersionId);
        }
        for (PnlResult pnl : result.getResults()) {
            final String sql = calculationVersionId == null
                    ? "INSERT INTO calc.pnl_result "
                    + "(batch_id, perspective, scope_code, result_status, version_no, calculated_at, confirmed_by, confirmed_at, "
                    + "published_by, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id"
                    : "INSERT INTO calc.pnl_result "
                    + "(batch_id, perspective, scope_code, result_status, version_no, calculated_at, confirmed_by, confirmed_at, "
                    + "published_by, published_at, calculation_version_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            Long resultId;
            if (calculationVersionId == null) {
                resultId = jdbc.queryForObject(sql, Long.class, batchId,
                        result.getPerspective().getCode(), pnl.getScopeCode(), result.getStatus().name(),
                        result.getVersionNo(), timestamp(result.getCalculatedAt()), result.getConfirmedBy(),
                        timestamp(result.getConfirmedAt()), result.getPublishedBy(), timestamp(result.getPublishedAt()));
            } else {
                resultId = jdbc.queryForObject(sql, Long.class, batchId,
                        result.getPerspective().getCode(), pnl.getScopeCode(), result.getStatus().name(),
                        result.getVersionNo(), timestamp(result.getCalculatedAt()), result.getConfirmedBy(),
                        timestamp(result.getConfirmedAt()), result.getPublishedBy(), timestamp(result.getPublishedAt()),
                        calculationVersionId);
            }
            for (Map.Entry<String, BigDecimal> line : pnl.getLines().entrySet()) {
                if (calculationVersionId == null) {
                    jdbc.update("INSERT INTO calc.pnl_result_line(result_id, line_code, line_value) VALUES (?, ?, ?)",
                            resultId, line.getKey(), line.getValue());
                } else {
                    // 本次修改：普通结果保存命中版本头时，结果行与结果头写入同一版本归属。
                    jdbc.update("INSERT INTO calc.pnl_result_line "
                                    + "(result_id, line_code, line_value, calculation_version_id) VALUES (?, ?, ?, ?)",
                            resultId, line.getKey(), line.getValue(), calculationVersionId);
                }
            }
        }
        return result;
    }

    @Override
    public Optional<CalculationResult> findByBatchNo(String batchNo) {
        String calculationVersionId = findReadableVersionId(batchNo);
        List<Header> headers = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? AND " + versionPredicate(calculationVersionId)
                        + " ORDER BY p.id", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), queryParameters(batchNo, calculationVersionId));
        return assemble(batchNo, headers);
    }

    @Override
    public Optional<CalculationResult> findByBatchNoForUser(String batchNo, String userId) {
        return findScoped(batchNo, userId, findReadableVersionId(batchNo));
    }

    @Override
    public Optional<CalculationResult> findVersionForUser(String batchNo, String versionNo, String userId) {
        List<String> ids = jdbc.query("SELECT calculation_version_id FROM calc.calculation_version "
                + "WHERE batch_no = ? AND version_no = ?", (rs, n) -> rs.getString(1), batchNo, versionNo);
        return ids.isEmpty() ? Optional.empty() : findScoped(batchNo, userId, ids.get(0));
    }

    private Optional<CalculationResult> findScoped(String batchNo, String userId, String calculationVersionId) {
        List<Header> headers = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? AND " + versionPredicate(calculationVersionId)
                        + " AND EXISTS (SELECT 1 FROM iam.user_scope s "
                        + "WHERE s.user_id = ? AND s.enabled = TRUE "
                        // 本次修改：已启用 ADMIN 跨视角读取全部结果范围，普通角色仍按视角和 scope 过滤。
                        + "AND (UPPER(s.role_code) = 'ADMIN' OR "
                        + "(s.perspective = p.perspective AND (s.scope_code = 'ALL' OR s.scope_code = p.scope_code)))) "
                        + "ORDER BY p.id", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), queryParameters(batchNo, calculationVersionId, userId));

        if (!headers.isEmpty()) return assemble(batchNo, headers);
        return Optional.empty();
    }

    @Override
    public Optional<CalculationResult> findMetadataByBatchNo(String batchNo) {
        String calculationVersionId = findReadableVersionId(batchNo);
        List<Header> metadata = jdbc.query("SELECT p.perspective, p.result_status, p.version_no, p.confirmed_by, "
                        + "p.calculated_at, p.confirmed_at, p.published_by, p.published_at, p.scope_code, p.id "
                        + "FROM calc.pnl_result p JOIN meta.analysis_batch b ON b.id = p.batch_id "
                        + "WHERE b.batch_no = ? AND " + versionPredicate(calculationVersionId)
                        + " ORDER BY p.id LIMIT 1", (rs, row) -> new Header(
                        AnalysisPerspective.fromCode(rs.getString("perspective")),
                        CalculationResultStatus.valueOf(rs.getString("result_status")),
                        rs.getString("version_no"), localDateTime(rs.getTimestamp("calculated_at")), rs.getString("confirmed_by"), localDateTime(rs.getTimestamp("confirmed_at")),
                        rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at")),
                        rs.getString("scope_code"), rs.getLong("id")), queryParameters(batchNo, calculationVersionId));
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
                first.status, first.confirmedBy, first.publishedBy, first.calculatedAt, first.confirmedAt, first.publishedAt,
                frozenFacts(batchNo, first.versionNo)));
    }

    private boolean isOrdinaryVersion(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT s.schema_version = 'ORDINARY-1' "
                + "FROM calc.calculation_version v JOIN calc.calculation_snapshot s ON s.snapshot_id = v.snapshot_id "
                + "WHERE v.calculation_version_id = ?", Boolean.class, id));
    }

    private void updateLifecycleOnly(CalculationResult result, String id) {
        Map<String, Object> version = jdbc.queryForMap("SELECT status, current_flag FROM calc.calculation_version "
                + "WHERE calculation_version_id = ? FOR UPDATE", id);
        String previous = (String) version.get("status");
        String next = result.getStatus().name();
        if (!Boolean.TRUE.equals(version.get("current_flag")) || !("CALCULATED".equals(previous) && "CONFIRMED".equals(next)
                || "CONFIRMED".equals(previous) && "PUBLISHED".equals(next))) {
            throw new IllegalStateException("禁止覆盖已保存版本或修改历史结果");
        }
        List<Header> headers = jdbc.query("SELECT id, scope_code FROM calc.pnl_result WHERE calculation_version_id = ?",
                (rs, row) -> new Header(result.getPerspective(), result.getStatus(), result.getVersionNo(),
                        null, null, null, null, null, rs.getString("scope_code"), rs.getLong("id")), id);
        if (headers.size() != result.getResults().size()) throw new IllegalStateException("生命周期操作不能改变结果范围");
        for (Header header : headers) {
            PnlResult incoming = result.getResults().stream().filter(p -> p.getScopeCode().equals(header.scopeCode))
                    .findFirst().orElseThrow(() -> new IllegalStateException("生命周期操作不能改变结果范围"));
            Map<String, BigDecimal> stored = new LinkedHashMap<>();
            jdbc.query("SELECT line_code, line_value FROM calc.pnl_result_line WHERE result_id = ?",
                    (RowCallbackHandler) rs -> stored.put(rs.getString(1), rs.getBigDecimal(2)), header.resultId);
            if (!stored.keySet().equals(incoming.getLines().keySet())) throw new IllegalStateException("禁止改写版本指标");
            for (String code : stored.keySet()) {
                if (stored.get(code).compareTo(incoming.getLines().get(code)) != 0) throw new IllegalStateException("禁止改写版本金额");
            }
        }
        jdbc.update("UPDATE calc.pnl_result SET result_status = ?, confirmed_by = ?, confirmed_at = ?, "
                + "published_by = ?, published_at = ? WHERE calculation_version_id = ?", next, result.getConfirmedBy(),
                timestamp(result.getConfirmedAt()), result.getPublishedBy(), timestamp(result.getPublishedAt()), id);
        jdbc.update("UPDATE calc.calculation_version SET status = ?, confirmed_by = ?, confirmed_at = ?, "
                + "published_by = ?, published_at = ? WHERE calculation_version_id = ?", next, result.getConfirmedBy(),
                timestamp(result.getConfirmedAt()), result.getPublishedBy(), timestamp(result.getPublishedAt()), id);
        jdbc.update("INSERT INTO flow.calculation_event_outbox(event_id, aggregate_type, aggregate_id, event_type, "
                + "calculation_version_id, payload_json) VALUES (?, 'CALCULATION_VERSION', ?, ?, ?, '{}'::jsonb)",
                "EVENT-" + java.util.UUID.randomUUID(), id, next, id);
    }

    private List<PnlFact> frozenFacts(String batchNo, String versionNo) {
        return jdbc.query("SELECT f.* FROM calc.calculation_version v "
                + "JOIN calc.calculation_snapshot s ON s.snapshot_id = v.snapshot_id "
                + "CROSS JOIN LATERAL jsonb_populate_recordset(NULL::raw.pnl_fact, s.content_json->'facts') f "
                + "WHERE v.batch_no = ? AND v.version_no = ? AND s.schema_version = 'ORDINARY-1' ORDER BY f.id",
                JdbcPnlFactDetailProvider::mapFact, batchNo, versionNo);
    }

    private void deleteResultDetails(Long batchId, String calculationVersionId) {
        String predicate = calculationVersionId == null
                ? "batch_id = ? AND calculation_version_id IS NULL"
                : "batch_id = ? AND calculation_version_id = ?";
        if (calculationVersionId == null) {
            jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                    + "(SELECT r.id FROM calc.pnl_result r WHERE " + predicate + ")", batchId);
            jdbc.update("DELETE FROM calc.pnl_result WHERE " + predicate, batchId);
        } else {
            jdbc.update("DELETE FROM calc.pnl_result_line WHERE result_id IN "
                    + "(SELECT r.id FROM calc.pnl_result r WHERE " + predicate + ")", batchId, calculationVersionId);
            jdbc.update("DELETE FROM calc.pnl_result WHERE " + predicate, batchId, calculationVersionId);
        }
    }

    private String findCalculationVersionId(Long batchId, CalculationResult result) {
        List<String> ids = jdbc.query("SELECT calculation_version_id FROM calc.calculation_version "
                        + "WHERE batch_id = ? AND perspective = ? AND version_no = ? "
                        + "ORDER BY current_flag DESC, calculated_at DESC, calculation_version_id DESC LIMIT 1",
                (rs, row) -> rs.getString("calculation_version_id"), batchId,
                result.getPerspective().getCode(), result.getVersionNo());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String findReadableVersionId(String batchNo) {
        List<String> ids = jdbc.query("SELECT v.calculation_version_id FROM calc.calculation_version v "
                        + "JOIN meta.analysis_batch b ON b.id = v.batch_id "
                        + "WHERE b.batch_no = ? ORDER BY v.current_flag DESC, v.calculated_at DESC, "
                        + "v.calculation_version_id DESC LIMIT 1",
                (rs, row) -> rs.getString("calculation_version_id"), batchNo);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String versionPredicate(String calculationVersionId) {
        return calculationVersionId == null
                ? "p.calculation_version_id IS NULL"
                : "p.calculation_version_id = ?";
    }

    private Object[] queryParameters(String batchNo, String calculationVersionId, Object... tail) {
        Object[] parameters = new Object[2 + tail.length];
        parameters[0] = batchNo;
        if (calculationVersionId != null) parameters[1] = calculationVersionId;
        int offset = calculationVersionId == null ? 1 : 2;
        for (int i = 0; i < tail.length; i++) parameters[offset + i] = tail[i];
        if (calculationVersionId == null && tail.length == 0) return new Object[]{batchNo};
        if (calculationVersionId == null) {
            Object[] compact = new Object[1 + tail.length];
            compact[0] = batchNo;
            System.arraycopy(tail, 0, compact, 1, tail.length);
            return compact;
        }
        return parameters;
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
