package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.CalculationVersionScopeResolver;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DATA-004-TECH-003-REVISION-003：来源版本 scope 解析契约。
 *
 * <p>本次新增测试：验证 SQL 按 calculation_version_id 读取真实 scope、历史无结果时失败关闭，
 * 并验证 db profile 创建生产实现而 local profile 不创建伪造实现。</p>
 */
class JdbcCalculationVersionScopeResolverContractTest {

    @Test
    void resolvesActualDistinctScopesByCalculationVersionId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("CALC-V-001")))
                .thenReturn(Arrays.asList("SCOPE-B", "SCOPE-A", "SCOPE-B"));
        JdbcCalculationVersionScopeResolver resolver =
                new JdbcCalculationVersionScopeResolver(jdbc);

        Set<String> scopes = resolver.resolve(sourceVersion("CALC-V-001"));

        assertEquals(Arrays.asList("SCOPE-B", "SCOPE-A"),
                Arrays.asList(scopes.toArray(new String[0])));
        verify(jdbc).query(
                org.mockito.ArgumentMatchers.contains("p.calculation_version_id = ?"),
                any(RowMapper.class), eq("CALC-V-001"));
        assertTrue(scopes.stream().noneMatch("ALL"::equals),
                "解析器不得在数据库没有 ALL 时伪造 ALL");
    }

    @Test
    void legacyVersionWithoutResultDetailsFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("LEGACY-V-001")))
                .thenReturn(Collections.<String>emptyList());
        JdbcCalculationVersionScopeResolver resolver =
                new JdbcCalculationVersionScopeResolver(jdbc);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(sourceVersion("LEGACY-V-001")));

        assertTrue(failure.getMessage().contains("LEGACY-V-001"));
    }

    @Test
    void blankScopeAlsoFailsClosed() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), eq("CALC-V-BLANK")))
                .thenReturn(Collections.singletonList("  "));
        JdbcCalculationVersionScopeResolver resolver =
                new JdbcCalculationVersionScopeResolver(jdbc);

        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(sourceVersion("CALC-V-BLANK")));
    }

    @Test
    void dbProfileWiresProductionResolver() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("db");
        context.register(DbProfileTestConfiguration.class, JdbcCalculationVersionScopeResolver.class);
        context.refresh();
        try {
            assertInstanceOf(JdbcCalculationVersionScopeResolver.class,
                    context.getBean(CalculationVersionScopeResolver.class));
        } finally {
            context.close();
        }
    }

    @Test
    void localProfileDoesNotInstallDbResolver() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("local");
        context.register(DbProfileTestConfiguration.class, JdbcCalculationVersionScopeResolver.class);
        context.refresh();
        try {
            assertThrows(org.springframework.beans.factory.NoSuchBeanDefinitionException.class,
                    () -> context.getBean(CalculationVersionScopeResolver.class));
        } finally {
            context.close();
        }
    }

    @Configuration
    @Profile("db")
    static class DbProfileTestConfiguration {
        @Bean
        DataSource dataSource() {
            return mock(DataSource.class);
        }
    }

    private CalculationVersion sourceVersion(String calculationVersionId) {
        return new CalculationVersion(calculationVersionId, "BATCH-001", AnalysisPerspective.BASE,
                "V001", "JOB-001", "SNAPSHOT-001", "SOURCE-HASH", "CONFIG-HASH",
                "INPUT-FINGERPRINT", CalculationVersionStatus.PUBLISHED, true, null,
                "TRACE-001", "calculator", LocalDateTime.of(2026, 10, 21, 10, 0),
                null, null, null, null);
    }
}
