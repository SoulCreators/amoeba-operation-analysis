package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.FcVersionProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 从数据库查询与分析批次匹配的 FC 版本元数据。 */
@Repository
@Profile("db")
public class JdbcFcVersionProvider implements FcVersionProvider {
    private final JdbcTemplate jdbc;

    public JdbcFcVersionProvider(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<FcVersion> findForBatch(String batchNo, AnalysisPerspective perspective, String versionCode) {
        String sql = "SELECT v.version_code, v.perspective, v.base_period, v.coverage_start, v.coverage_end, "
                + "v.status, v.uploaded_by, v.uploaded_at, v.official, v.published_by, v.published_at "
                + "FROM calc.fc_version v JOIN meta.analysis_batch b ON b.period = v.base_period "
                + "WHERE b.batch_no = ? AND v.perspective = ? "
                + "AND ((? IS NULL AND v.official = TRUE) OR v.version_code = ?) "
                + "ORDER BY v.official DESC, v.updated_at DESC LIMIT 1";
        List<FcVersion> values = jdbc.query(sql, (rs, row) -> new FcVersion(
                rs.getString("version_code"), AnalysisPerspective.fromCode(rs.getString("perspective")),
                rs.getString("base_period"), rs.getString("coverage_start"), rs.getString("coverage_end"),
                rs.getString("status"), rs.getString("uploaded_by"), localDateTime(rs.getTimestamp("uploaded_at")),
                rs.getBoolean("official"), rs.getString("published_by"), localDateTime(rs.getTimestamp("published_at"))),
                batchNo, perspective.getCode(), versionCode, versionCode);
        return values.isEmpty() ? Optional.<FcVersion>empty() : Optional.of(values.get(0));
    }

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
