package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CALC-JDBC-FACT-001：锁定 JdbcPnlFactProvider.load 当前 JDBC 参数、SQL 及空结果行为。
 */
class JdbcPnlFactProviderContractTest {

    /**
     * 本次修改验证当前 SQL 的事实表、批次子查询、视角过滤及 scope_code 分组排序，并核对参数透传。
     */
    @Test
    void loadUsesCurrentSqlAndBindsBatchNoAndPerspective() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcPnlFactProvider provider = providerWithJdbc(jdbc);
        AnalysisBatch batch = batch("BATCH-CALC-001", AnalysisPerspective.PRODUCT_LINE);
        List<PnlFact> expected = Collections.singletonList(mock(PnlFact.class));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-CALC-001"), eq("PRODUCT_LINE"))).thenReturn(expected);

        List<PnlFact> actual = provider.load(batch);

        assertSame(expected, actual);
        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-CALC-001"), eq("PRODUCT_LINE"));

        String normalized = sql.getValue().toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalized.contains("from raw.pnl_fact"),
                "SQL must read from raw.pnl_fact");
        assertTrue(normalized.contains("from meta.analysis_batch where batch_no = ?"),
                "batch must be bound through meta.analysis_batch.batch_no");
        assertTrue(normalized.contains("and perspective = ?"),
                "perspective must be parameter-bound");
        assertTrue(normalized.contains("group by scope_code"),
                "SQL must group by scope_code");
        assertTrue(normalized.contains("order by scope_code"),
                "SQL must order by scope_code");
    }

    /**
     * 本次修改锁定 JdbcTemplate 返回空列表时的既有 IllegalStateException 类型和完整错误消息。
     */
    @Test
    void loadThrowsExistingExceptionWhenJdbcReturnsEmptyList() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcPnlFactProvider provider = providerWithJdbc(jdbc);
        AnalysisBatch batch = batch("BATCH-CALC-EMPTY", AnalysisPerspective.BASE);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-CALC-EMPTY"), eq("BASE"))).thenReturn(Collections.emptyList());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> provider.load(batch));

        assertEquals("标准事实表没有可计算数据: BATCH-CALC-EMPTY", failure.getMessage());
        verify(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-CALC-EMPTY"), eq("BASE"));
    }

    /**
     * 本次修改验证 JdbcTemplate 的 RowMapper 结果列表由 load 原样返回，不发生复制或重组。
     */
    @Test
    void loadReturnsJdbcTemplateResultsAsIs() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcPnlFactProvider provider = providerWithJdbc(jdbc);
        AnalysisBatch batch = batch("BATCH-CALC-RESULT", AnalysisPerspective.BASE);
        List<PnlFact> expected = Collections.singletonList(mock(PnlFact.class));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-CALC-RESULT"), eq("BASE"))).thenReturn(expected);

        List<PnlFact> actual = provider.load(batch);

        assertSame(expected, actual);
    }

    /**
     * 本次修改按要求通过反射把 mock JdbcTemplate 注入生产类的私有字段，避免引入真实数据源。
     */
    private static JdbcPnlFactProvider providerWithJdbc(JdbcTemplate jdbc) throws Exception {
        JdbcPnlFactProvider provider = new JdbcPnlFactProvider(mock(DataSource.class));
        java.lang.reflect.Field field = JdbcPnlFactProvider.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(provider, jdbc);
        return provider;
    }

    /**
     * 本次修改构造仅用于契约测试的批次对象，保留真实 batchNo 和 perspective 透传值。
     */
    private static AnalysisBatch batch(String batchNo, AnalysisPerspective perspective) {
        return new AnalysisBatch(1L, batchNo, "2026-10", perspective, BatchStatus.DRAFT,
                "contract-test", LocalDateTime.now());
    }
}
