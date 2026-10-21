package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import com.sunwoda.evb.finance.analysis.domain.repository.ManualCalculationRevisionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 人工修订关系仓储。
 *
 * <p>本次新增：把来源版本锁定、来源版本切换、目标版本追加和修订审计写入
 * 同一个 Spring JDBC 事务，保证人工修订不会覆盖旧版本，也不会留下半条版本链。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcManualCalculationRevisionRepository implements ManualCalculationRevisionRepository {
    private static final String VERSION_COLUMNS =
            "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, snapshot_id, "
                    + "source_version_hash, config_hash, input_fingerprint, status, current_flag, "
                    + "supersedes_version_id, trace_id, calculated_by, calculated_at, confirmed_by, "
                    + "confirmed_at, published_by, published_at";

    private final JdbcTemplate jdbc;

    public JdbcManualCalculationRevisionRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource 不能为空");
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 本次新增：以来源版本行锁作为并发边界，先切换旧版本再追加新版本和审计关系；
     * V026 的 current 唯一索引与 V027 的外键/唯一约束继续作为数据库兜底。
     */
    @Override
    @Transactional
    public CalculationVersion append(CalculationVersion sourceVersion,
                                     CalculationVersion targetVersion,
                                     ManualCalculationRevision revision) {
        validateArguments(sourceVersion, targetVersion, revision);
        VersionRow source = lockSource(sourceVersion.getCalculationVersionId());
        validateSource(source, sourceVersion);
        validateTarget(source, targetVersion, revision);

        // 本次新增：来源版本只改变当前指针和生命周期状态，历史版本主键及结果明细不被覆盖。
        jdbc.update("UPDATE calc.calculation_version SET current_flag = FALSE, status = 'SUPERSEDED' "
                        + "WHERE calculation_version_id = ? AND current_flag = TRUE",
                source.versionId);

        // 本次新增：目标版本沿用来源批次，新增独立版本主键并保留 supersedes 关系。
        jdbc.update("INSERT INTO calc.calculation_version ("
                        + "calculation_version_id, batch_id, batch_no, perspective, version_no, job_id, "
                        + "snapshot_id, source_version_hash, config_hash, input_fingerprint, status, "
                        + "current_flag, supersedes_version_id, trace_id, calculated_by, calculated_at, "
                        + "confirmed_by, confirmed_at, published_by, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                targetVersion.getCalculationVersionId(), source.batchId, targetVersion.getBatchNo(),
                targetVersion.getPerspective().getCode(), targetVersion.getVersionNo(), targetVersion.getJobId(),
                targetVersion.getSnapshotId(), targetVersion.getSourceVersionHash(), targetVersion.getConfigHash(),
                targetVersion.getInputFingerprint(), targetVersion.getStatus().name(), targetVersion.isCurrent(),
                targetVersion.getSupersedesVersionId(), targetVersion.getTraceId(), targetVersion.getCalculatedBy(),
                timestamp(targetVersion.getCalculatedAt()), targetVersion.getConfirmedBy(),
                timestamp(targetVersion.getConfirmedAt()), targetVersion.getPublishedBy(),
                timestamp(targetVersion.getPublishedAt()));

        // 本次新增：人工修订只追加关系，操作者、原因、时间和追踪号永久保留。
        jdbc.update("INSERT INTO calc.calculation_manual_revision ("
                        + "revision_id, source_calculation_version_id, target_calculation_version_id, "
                        + "revised_by, revision_reason, revised_at, trace_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                revision.getRevisionId(), revision.getSourceCalculationVersionId(),
                revision.getTargetCalculationVersionId(), revision.getRevisedBy(), revision.getRevisionReason(),
                timestamp(revision.getRevisedAt()), revision.getTraceId());

        // 本次新增：人工修订复制来源版本的结果头和结果行，目标版本只追加新主键，绝不改写来源明细。
        copyResultDetails(source.versionId, targetVersion);
        return targetVersion;
    }

    /**
     * 本次新增：复制结果范围及其明细时同时写入目标 calculation_version_id，保证同批次多版本可独立读取。
     * 结果行版本列由后续数据库迁移提供；未具备该列的旧数据库应在合约测试中显式暴露为数据库缺口。
     */
    private void copyResultDetails(String sourceVersionId, CalculationVersion targetVersion) {
        jdbc.update("INSERT INTO calc.pnl_result ("
                        + "batch_id, perspective, scope_code, result_status, version_no, calculated_at, "
                        + "confirmed_by, confirmed_at, published_by, published_at, calculation_version_id) "
                        + "SELECT batch_id, perspective, scope_code, ?, ?, ?, ?, ?, ?, ?, ? "
                        + "FROM calc.pnl_result WHERE calculation_version_id = ?",
                targetVersion.getStatus().name(), targetVersion.getVersionNo(),
                timestamp(targetVersion.getCalculatedAt()), targetVersion.getConfirmedBy(),
                timestamp(targetVersion.getConfirmedAt()), targetVersion.getPublishedBy(),
                timestamp(targetVersion.getPublishedAt()), targetVersion.getCalculationVersionId(),
                sourceVersionId);

        jdbc.update("INSERT INTO calc.pnl_result_line "
                        + "(result_id, line_code, line_value, calculation_version_id) "
                        + "SELECT target_result.id, source_line.line_code, source_line.line_value, ? "
                        + "FROM calc.pnl_result source_result "
                        + "JOIN calc.pnl_result_line source_line ON source_line.result_id = source_result.id "
                        + "JOIN calc.pnl_result target_result ON target_result.calculation_version_id = ? "
                        + "AND target_result.perspective = source_result.perspective "
                        + "AND target_result.scope_code = source_result.scope_code "
                        + "WHERE source_result.calculation_version_id = ?",
                targetVersion.getCalculationVersionId(), targetVersion.getCalculationVersionId(), sourceVersionId);
    }

    @Override
    public Optional<ManualCalculationRevision> findByTargetVersionId(String targetVersionId) {
        String value = required(targetVersionId, "targetVersionId");
        List<ManualCalculationRevision> revisions = jdbc.query(
                "SELECT revision_id, source_calculation_version_id, target_calculation_version_id, "
                        + "revised_by, revision_reason, revised_at, trace_id "
                        + "FROM calc.calculation_manual_revision WHERE target_calculation_version_id = ?",
                (rs, rowNum) -> mapRevision(rs), value);
        return revisions.isEmpty() ? Optional.<ManualCalculationRevision>empty()
                : Optional.of(revisions.get(0));
    }

    private VersionRow lockSource(String sourceVersionId) {
        List<VersionRow> rows = jdbc.query("SELECT " + VERSION_COLUMNS
                        + " FROM calc.calculation_version WHERE calculation_version_id = ? FOR UPDATE",
                (rs, rowNum) -> mapVersion(rs), sourceVersionId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("来源计算版本不存在: " + sourceVersionId);
        }
        return rows.get(0);
    }

    private void validateArguments(CalculationVersion sourceVersion,
                                   CalculationVersion targetVersion,
                                   ManualCalculationRevision revision) {
        if (sourceVersion == null || targetVersion == null || revision == null) {
            throw new IllegalArgumentException("人工修订参数不能为空");
        }
        if (!sourceVersion.getCalculationVersionId().equals(revision.getSourceCalculationVersionId())
                || !targetVersion.getCalculationVersionId().equals(revision.getTargetCalculationVersionId())) {
            throw new IllegalArgumentException("修订关系的来源/目标版本与版本参数不一致");
        }
    }

    private void validateSource(VersionRow source, CalculationVersion expected) {
        if (!source.versionId.equals(expected.getCalculationVersionId())
                || !source.batchNo.equals(expected.getBatchNo())
                || !source.perspective.equals(expected.getPerspective().getCode())) {
            throw new IllegalArgumentException("来源版本参数与数据库记录不一致");
        }
        if (!source.current) {
            throw new IllegalStateException("只有当前版本才能进行人工修订");
        }
    }

    private void validateTarget(VersionRow source, CalculationVersion target,
                                ManualCalculationRevision revision) {
        if (!target.isCurrent() || target.getStatus() == CalculationVersionStatus.SUPERSEDED) {
            throw new IllegalArgumentException("人工修订目标必须是新的当前版本");
        }
        if (!source.batchNo.equals(target.getBatchNo())
                || !source.perspective.equals(target.getPerspective().getCode())) {
            throw new IllegalArgumentException("目标版本必须属于来源版本的同一批次和视角");
        }
        if (!source.versionId.equals(target.getSupersedesVersionId())) {
            throw new IllegalArgumentException("目标版本必须明确替代来源版本");
        }
        if (!target.getCalculationVersionId().equals(revision.getTargetCalculationVersionId())) {
            throw new IllegalArgumentException("目标版本与修订关系不一致");
        }
    }

    private VersionRow mapVersion(ResultSet rs) throws SQLException {
        return new VersionRow(rs.getString("calculation_version_id"), rs.getLong("batch_id"),
                rs.getString("batch_no"), rs.getString("perspective"), rs.getBoolean("current_flag"));
    }

    private ManualCalculationRevision mapRevision(ResultSet rs) throws SQLException {
        Timestamp revisedAt = rs.getTimestamp("revised_at");
        return new ManualCalculationRevision(rs.getString("revision_id"),
                rs.getString("source_calculation_version_id"), rs.getString("target_calculation_version_id"),
                rs.getString("revised_by"), rs.getString("revision_reason"),
                revisedAt.toLocalDateTime(), rs.getString("trace_id"));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    private static final class VersionRow {
        private final String versionId;
        private final long batchId;
        private final String batchNo;
        private final String perspective;
        private final boolean current;

        private VersionRow(String versionId, long batchId, String batchNo,
                           String perspective, boolean current) {
            this.versionId = versionId;
            this.batchId = batchId;
            this.batchNo = batchNo;
            this.perspective = perspective;
            this.current = current;
        }
    }
}
