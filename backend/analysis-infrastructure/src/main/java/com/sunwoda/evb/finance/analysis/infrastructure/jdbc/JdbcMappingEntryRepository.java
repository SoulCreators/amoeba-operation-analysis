package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import com.sunwoda.evb.finance.analysis.domain.repository.MappingEntryRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@Primary
@Profile("db")
public class JdbcMappingEntryRepository implements MappingEntryRepository {
    private final JdbcTemplate jdbc;

    public JdbcMappingEntryRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public MappingEntry save(MappingEntry entry) {
        jdbc.update("INSERT INTO cfg.mapping_entry(mapping_type, match_key, mapped_value, enabled, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (mapping_type, match_key) DO UPDATE SET "
                        + "mapped_value = EXCLUDED.mapped_value, enabled = EXCLUDED.enabled, updated_by = EXCLUDED.updated_by, "
                        + "updated_at = CURRENT_TIMESTAMP", entry.getType().name(), entry.getMatchKey(),
                entry.getMappedValue(), entry.isEnabled(), entry.getUpdatedBy());
        return find(entry.getType(), entry.getMatchKey()).orElse(entry);
    }

    @Override
    public Optional<MappingEntry> find(MappingType type, String matchKey) {
        return jdbc.query("SELECT id, mapping_type, match_key, mapped_value, enabled, updated_by, updated_at "
                        + "FROM cfg.mapping_entry WHERE mapping_type = ? AND match_key = ?", rs -> {
                    if (!rs.next()) return Optional.<MappingEntry>empty();
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return Optional.of(new MappingEntry(rs.getLong("id"),
                            MappingType.valueOf(rs.getString("mapping_type")), rs.getString("match_key"),
                            rs.getString("mapped_value"), rs.getBoolean("enabled"), rs.getString("updated_by"),
                            updated == null ? null : updated.toLocalDateTime()));
                }, type.name(), matchKey);
    }

    @Override
    public List<MappingEntry> list(MappingType type, boolean includeDisabled) {
        return jdbc.query("SELECT id, mapping_type, match_key, mapped_value, enabled, updated_by, updated_at "
                        + "FROM cfg.mapping_entry WHERE mapping_type = ? AND (? OR enabled = TRUE) ORDER BY match_key",
                (rs, row) -> new MappingEntry(rs.getLong("id"), MappingType.valueOf(rs.getString("mapping_type")),
                        rs.getString("match_key"), rs.getString("mapped_value"), rs.getBoolean("enabled"),
                        rs.getString("updated_by"), rs.getTimestamp("updated_at") == null ? null
                                : rs.getTimestamp("updated_at").toLocalDateTime()), type.name(), includeDisabled);
    }
}
