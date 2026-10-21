package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V026 计算快照 JDBC 合约测试。
 *
 * <p>本次新增：使用可记录的 JdbcTemplate 验证 SQL 列、JSONB 转换、保存/读取映射，
 * 并用 Spring Profile 验证数据库实现不会污染 local Profile；全程不连接真实 PostgreSQL。</p>
 */
class JdbcCalculationSnapshotRepositoryContractTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 21, 11, 30);

    @Test
    void savesAllV026ColumnsAndCastsJsonFieldsToJsonb() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        JdbcCalculationSnapshotRepository repository = new JdbcCalculationSnapshotRepository(jdbc);
        CalculationSnapshot snapshot = snapshot();

        assertSame(snapshot, repository.save(snapshot));

        assertTrue(jdbc.updateSql.contains("calc.calculation_snapshot"));
        assertTrue(jdbc.updateSql.contains("snapshot_id, snapshot_type, schema_version"));
        assertTrue(jdbc.updateSql.contains("content_json, source_version_set"));
        assertTrue(jdbc.updateSql.contains("?::jsonb, ?::jsonb"));
        assertTrue(jdbc.updateSql.contains("ON CONFLICT (snapshot_id) DO NOTHING"));
        assertEquals(10, jdbc.updateArgs.length);
        assertEquals("SNAP-001", jdbc.updateArgs[0]);
        assertEquals("{\"mapping\":\"v1\"}", jdbc.updateArgs[3]);
        assertEquals("[\"MAP-001\",\"RULE-001\"]", jdbc.updateArgs[4]);
        assertEquals(Timestamp.valueOf(CREATED_AT), jdbc.updateArgs[9]);
    }

    @Test
    void readsAllColumnsIncludingJsonbAsOriginalJsonText() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("snapshot_id")).thenReturn("SNAP-001");
        when(rs.getString("snapshot_type")).thenReturn("INPUT");
        when(rs.getString("schema_version")).thenReturn("V026-1");
        when(rs.getString("content_json")).thenReturn("{\"mapping\":\"v1\"}");
        when(rs.getString("source_version_set")).thenReturn("[\"MAP-001\",\"RULE-001\"]");
        when(rs.getString("content_hash")).thenReturn("HASH-CONTENT");
        when(rs.getString("source_version_hash")).thenReturn("HASH-SOURCE");
        when(rs.getString("rule_version")).thenReturn("RULE-2026");
        when(rs.getString("created_by")).thenReturn("calculator");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(CREATED_AT));

        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate(rs);
        JdbcCalculationSnapshotRepository repository = new JdbcCalculationSnapshotRepository(jdbc);

        Optional<CalculationSnapshot> found = repository.findById("SNAP-001");

        assertTrue(found.isPresent());
        assertEquals("{\"mapping\":\"v1\"}", found.get().getContentJson());
        assertEquals("[\"MAP-001\",\"RULE-001\"]", found.get().getSourceVersionSetJson());
        assertEquals(CREATED_AT, found.get().getCreatedAt());
        assertTrue(jdbc.querySql.contains("SELECT snapshot_id, snapshot_type, schema_version"));
        assertTrue(jdbc.querySql.contains("content_json, source_version_set"));
        assertEquals("SNAP-001", jdbc.queryArgs[0]);
    }

    @Test
    void dbProfileProvidesPrimaryRepositoryAndLocalProfileDoesNot() {
        AnnotationConfigApplicationContext dbContext = new AnnotationConfigApplicationContext();
        dbContext.getEnvironment().setActiveProfiles("db");
        dbContext.register(ProfileDataSourceConfiguration.class, JdbcCalculationSnapshotRepository.class);
        dbContext.refresh();
        try {
            assertInstanceOf(JdbcCalculationSnapshotRepository.class,
                    dbContext.getBean(CalculationSnapshotRepository.class));
        } finally {
            dbContext.close();
        }

        AnnotationConfigApplicationContext localContext = new AnnotationConfigApplicationContext();
        localContext.getEnvironment().setActiveProfiles("local");
        localContext.register(ProfileDataSourceConfiguration.class, JdbcCalculationSnapshotRepository.class);
        localContext.refresh();
        try {
            assertThrows(org.springframework.beans.factory.NoSuchBeanDefinitionException.class,
                    () -> localContext.getBean(CalculationSnapshotRepository.class));
        } finally {
            localContext.close();
        }
    }

    private static CalculationSnapshot snapshot() {
        return new CalculationSnapshot("SNAP-001", "INPUT", "V026-1",
                "{\"mapping\":\"v1\"}", "[\"MAP-001\",\"RULE-001\"]",
                "HASH-CONTENT", "HASH-SOURCE", "RULE-2026", "calculator", CREATED_AT);
    }

    @Configuration
    @Profile("db")
    static class ProfileDataSourceConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }
    }

    /** 本次新增：只记录 JdbcTemplate 调用并驱动 RowMapper，避免测试依赖真实 PostgreSQL。 */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String updateSql;
        private Object[] updateArgs;
        private String querySql;
        private Object[] queryArgs;
        private final ResultSet resultSet;

        private RecordingJdbcTemplate() {
            this(null);
        }

        private RecordingJdbcTemplate(ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        @Override
        public int update(String sql, Object... args) {
            this.updateSql = sql;
            this.updateArgs = args;
            return 1;
        }

        @Override
        public <T> T query(String sql, ResultSetExtractor<T> extractor, Object... args) {
            this.querySql = sql;
            this.queryArgs = args;
            try {
                return extractor.extractData(resultSet);
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
