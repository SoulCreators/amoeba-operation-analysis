package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SQL gate for the trace query adapter. The query must enforce authorization
 * in JDBC, rather than returning all source rows to the application service.
 */
class JdbcPnlFactSourceTraceQuerySecurityContractTest {
    @Test
    void traceSqlBindsBatchPerspectiveAndTaskAndContainsAllSecurityPredicates()
            throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcPnlFactSourceProvider provider = new JdbcPnlFactSourceProvider(jdbc);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-001"), eq("BASE"), eq("TASK-001"), eq("U001")))
                .thenReturn(Collections.emptyList());

        provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", "U001");

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-001"), eq("BASE"), eq("TASK-001"), eq("U001"));

        String normalized = sql.getValue().toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalized.contains("b.batch_no = ?"), "batch must be parameter-bound");
        assertTrue(normalized.contains("t.source_task_no = ?"),
                "source task must be parameter-bound");
        assertTrue(normalized.contains("s.user_id = ?"),
                "user identity must be parameter-bound");
        assertTrue(normalized.contains("scope_code"),
                "trace SQL must include scope authorization");
        assertTrue(normalized.contains("enabled"),
                "trace SQL must ignore disabled grants");
        assertTrue(normalized.contains("can_view_detail"),
                "trace SQL must require detail capability");
        assertTrue(normalized.contains("iam.user_scope"),
                "trace SQL must join user scope grants");
    }

    @Test
    void querySqlIsNotAllowedToUseStringConcatenationForTraceParameters() throws Exception {
        java.lang.reflect.Field field = JdbcPnlFactSourceProvider.class
                .getDeclaredField("FIND_FOR_USER_SQL");
        field.setAccessible(true);
        String querySql = (String) field.get(null);

        assertTrue(querySql.contains("b.batch_no = ?"));
        assertTrue(querySql.contains("t.source_task_no = ?"));
        assertTrue(querySql.contains("s.user_id = ?"));
        assertTrue(querySql.contains("?"));
    }
}
