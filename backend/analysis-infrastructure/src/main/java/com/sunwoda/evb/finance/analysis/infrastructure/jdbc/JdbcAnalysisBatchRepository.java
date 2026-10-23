package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcAnalysisBatchRepository implements AnalysisBatchRepository {
    private final JdbcTemplate jdbc;

    public JdbcAnalysisBatchRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public AnalysisBatch save(AnalysisBatch batch) {
        if (batch.getId() == null) {
            Long id = jdbc.queryForObject("INSERT INTO meta.analysis_batch "
                    + "(batch_no, period, perspective, scope_code, status, created_by, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                    batch.getBatchNo(), batch.getPeriod(), batch.getPerspective().getCode(),
                    batch.getScopeCode(), batch.getStatus().name(), batch.getCreatedBy(),
                    Timestamp.valueOf(batch.getCreatedAt()), Timestamp.valueOf(batch.getUpdatedAt()));
            return new AnalysisBatch(id, batch.getBatchNo(), batch.getPeriod(), batch.getPerspective(),
                    batch.getScopeCode(), batch.getStatus(), batch.getCreatedBy(), batch.getCreatedAt());
        }
        jdbc.update("UPDATE meta.analysis_batch SET perspective = ?, status = ?, updated_at = ? WHERE id = ?",
                batch.getPerspective().getCode(), batch.getStatus().name(), Timestamp.valueOf(batch.getUpdatedAt()),
                batch.getId());
        return batch;
    }

    @Override
    public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
        return jdbc.query("SELECT id, batch_no, period, perspective, scope_code, status, created_by, created_at "
                        + "FROM meta.analysis_batch WHERE batch_no = ?", rs -> {
                    if (!rs.next()) return Optional.<AnalysisBatch>empty();
                    Timestamp created = rs.getTimestamp("created_at");
                    return Optional.of(new AnalysisBatch(rs.getLong("id"), rs.getString("batch_no"),
                            rs.getString("period"), AnalysisPerspective.fromCode(rs.getString("perspective")),
                            rs.getString("scope_code"),
                            BatchStatus.valueOf(rs.getString("status")), rs.getString("created_by"),
                            created == null ? LocalDateTime.now() : created.toLocalDateTime()));
                }, batchNo);
    }

    @Override
    public List<AnalysisBatch> findByPerspectiveAndPeriodRange(AnalysisPerspective perspective,
                                                               String fromPeriod, String toPeriod) {
        return jdbc.query("SELECT id, batch_no, period, perspective, scope_code, status, created_by, created_at "
                        + "FROM meta.analysis_batch "
                        + "WHERE perspective = ? AND period >= ? AND period <= ? ORDER BY period ASC",
                (rs, rowNum) -> {
                    Timestamp created = rs.getTimestamp("created_at");
                    return new AnalysisBatch(rs.getLong("id"), rs.getString("batch_no"),
                            rs.getString("period"), AnalysisPerspective.fromCode(rs.getString("perspective")),
                            rs.getString("scope_code"), BatchStatus.valueOf(rs.getString("status")),
                            rs.getString("created_by"),
                            created == null ? LocalDateTime.now() : created.toLocalDateTime());
                },
                perspective.getCode(), fromPeriod, toPeriod);
    }
}
