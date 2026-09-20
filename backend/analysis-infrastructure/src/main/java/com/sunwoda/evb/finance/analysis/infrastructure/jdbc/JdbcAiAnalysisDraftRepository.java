package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcAiAnalysisDraftRepository implements AiAnalysisDraftRepository {
    private final JdbcTemplate jdbc;

    public JdbcAiAnalysisDraftRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public synchronized AiAnalysisDraft save(AiAnalysisDraft draft) {
        Long batchId = jdbc.queryForObject("SELECT id FROM meta.analysis_batch WHERE batch_no = ?",
                Long.class, draft.getBatchNo());
        String sections = encodeSections(draft.getSections());
        jdbc.update("INSERT INTO calc.ai_analysis_draft(batch_id, result_version_no, status, generated_by, generated_at, "
                        + "published_by, published_at, sections_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (batch_id, result_version_no) DO UPDATE SET status = EXCLUDED.status, "
                        + "published_by = EXCLUDED.published_by, published_at = EXCLUDED.published_at, sections_json = EXCLUDED.sections_json",
                batchId, draft.getVersionNo(), draft.getStatus().name(), draft.getGeneratedBy(),
                Timestamp.valueOf(draft.getGeneratedAt()), draft.getPublishedBy(),
                draft.getPublishedAt() == null ? null : Timestamp.valueOf(draft.getPublishedAt()), sections);
        return draft;
    }

    @Override
    public Optional<AiAnalysisDraft> findByBatchNo(String batchNo) {
        return jdbc.query("SELECT d.result_version_no, d.status, d.generated_by, d.generated_at, d.published_by, "
                        + "d.published_at, d.sections_json FROM calc.ai_analysis_draft d JOIN meta.analysis_batch b "
                        + "ON b.id = d.batch_id WHERE b.batch_no = ?", rs -> {
                    if (!rs.next()) return Optional.<AiAnalysisDraft>empty();
                    Timestamp generated = rs.getTimestamp("generated_at");
                    Timestamp published = rs.getTimestamp("published_at");
                    return Optional.of(new AiAnalysisDraft(batchNo, rs.getString("result_version_no"),
                            decodeSections(rs.getString("sections_json")), rs.getString("generated_by"),
                            generated == null ? LocalDateTime.now() : generated.toLocalDateTime(),
                            AiAnalysisDraftStatus.valueOf(rs.getString("status")), rs.getString("published_by"),
                            published == null ? null : published.toLocalDateTime()));
                }, batchNo);
    }

    private String encodeSections(Map<String, String> sections) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (value.length() > 0) value.append('|');
            value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return value.toString();
    }

    private Map<String, String> decodeSections(String value) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        if (value == null || value.isEmpty()) return sections;
        for (String item : value.split("\\|")) {
            String[] pair = item.split("=", 2);
            if (pair.length == 2) sections.put(decode(pair[0]), decode(pair[1]));
        }
        return sections;
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
