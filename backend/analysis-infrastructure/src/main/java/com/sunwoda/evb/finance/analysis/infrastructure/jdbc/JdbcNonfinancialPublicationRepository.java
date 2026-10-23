package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublicationStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialPublicationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 非财经指标发布记录 JDBC 持久化适配器，按期间 upsert。
 */
@Repository
@Primary
@Profile("db")
public class JdbcNonfinancialPublicationRepository implements NonfinancialPublicationRepository {
    private static final String UPSERT_SQL =
            "INSERT INTO flow.nonfinancial_publication "
                    + "(period, status, conflict_count, published_by, published_at) "
                    + "VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT (period) DO UPDATE SET status = EXCLUDED.status, "
                    + "conflict_count = EXCLUDED.conflict_count, "
                    + "published_by = EXCLUDED.published_by, "
                    + "published_at = EXCLUDED.published_at";

    private static final String SELECT_COLUMNS =
            "period, status, conflict_count, published_by, published_at";

    private final JdbcTemplate jdbc;

    public JdbcNonfinancialPublicationRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public NonfinancialPublication save(NonfinancialPublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("publication 不能为空");
        }
        Timestamp publishedAt = publication.getPublishedAt() == null
                ? null : Timestamp.valueOf(publication.getPublishedAt());
        jdbc.update(UPSERT_SQL, publication.getPeriod(), publication.getStatus().name(),
                publication.getConflictCount(), publication.getPublishedBy(), publishedAt);
        return publication;
    }

    @Override
    public Optional<NonfinancialPublication> findByPeriod(String period) {
        List<NonfinancialPublication> result = jdbc.query(
                "SELECT " + SELECT_COLUMNS + " FROM flow.nonfinancial_publication "
                        + "WHERE period = ?",
                (rs, rowNum) -> map(rs), requiredText(period, "period"));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    private NonfinancialPublication map(ResultSet rs) throws SQLException {
        Timestamp published = rs.getTimestamp("published_at");
        LocalDateTime publishedAt = published == null ? null : published.toLocalDateTime();
        return new NonfinancialPublication(rs.getString("period"),
                NonfinancialPublicationStatus.valueOf(rs.getString("status")),
                rs.getInt("conflict_count"), rs.getString("published_by"), publishedAt);
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value;
    }
}
