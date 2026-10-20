package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FACT-DETAIL-JDBC-PG-SEC-001：锁定 JdbcPnlFactDetailProvider 的输入与 SQL 绑定契约。
 *
 * <p>本测试只使用 Mockito/JdbcTemplate，不连接真实数据库，覆盖输入校验、trim 后参数顺序、
 * 批次/视角/用户及明细权限范围 SQL，以及事实明细 RowMapper 和空列表语义。</p>
 */
class JdbcPnlFactDetailProviderInputContractTest {
    private JdbcTemplate jdbc;
    private JdbcPnlFactDetailProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        // 本次新增通过 mock JdbcTemplate 隔离数据库，并用反射替换生产类内部的数据访问对象。
        jdbc = mock(JdbcTemplate.class);
        provider = new JdbcPnlFactDetailProvider(mock(javax.sql.DataSource.class));
        java.lang.reflect.Field field = JdbcPnlFactDetailProvider.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(provider, jdbc);
    }

    @Test
    void rejectsNullOrBlankBatchNoBeforeTouchingJdbc() {
        // 本次新增锁定 batchNo 对 null、空串和全空白输入均拒绝且不访问 JDBC。
        assertInvalid("batchNo must not be blank",
                () -> provider.findForUser(null, AnalysisPerspective.BASE, "U001"));
        assertInvalid("batchNo must not be blank",
                () -> provider.findForUser(" \t", AnalysisPerspective.BASE, "U001"));
    }

    @Test
    void rejectsNullPerspectiveBeforeTouchingJdbc() {
        // 本次新增锁定 perspective 缺失时的异常语义，并确认校验在 JDBC 之前短路。
        assertInvalid("perspective must not be null",
                () -> provider.findForUser("BATCH-001", null, "U001"));
    }

    @Test
    void rejectsNullOrBlankUserIdBeforeTouchingJdbc() {
        // 本次新增锁定 userId 对 null 和全空白输入均拒绝且不触发 SQL 查询。
        assertInvalid("userId must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, null));
        assertInvalid("userId must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "\n"));
    }

    @Test
    void trimsInputsAndBindsBatchPerspectiveAndUserInSqlOrder() {
        // 本次新增锁定 batchNo、perspective、userId trim 后按 SQL 占位顺序绑定，且返回 JDBC 原列表。
        List<PnlFact> expected = Collections.emptyList();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("PRODUCT_LINE"),
                eq("U001"))).thenReturn(expected);

        List<PnlFact> actual = provider.findForUser(
                "  BATCH-001  ", AnalysisPerspective.PRODUCT_LINE, "\tU001\n");

        assertSame(expected, actual);
        verify(jdbc).query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("PRODUCT_LINE"),
                eq("U001"));
    }

    @Test
    void sqlUsesBatchPerspectiveUserAndDetailScopePredicates() {
        // 本次新增独立锁定 SQL 的批次、视角、用户、启用、明细权限和 scope 占位条件。
        when(jdbc.query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"), eq("U001")))
                .thenReturn(Collections.emptyList());

        provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "U001");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"), eq("U001"));
        String normalized = sql.getValue().toLowerCase(Locale.ROOT);
        // 本次新增锁定批次、视角、用户均使用 JDBC 占位符，避免拼接输入形成 SQL 注入面。
        assertTrue(normalized.contains("where b.batch_no = ?"),
                "batchNo must be parameter-bound through analysis_batch");
        assertTrue(normalized.contains("and f.perspective = ?"),
                "perspective must be parameter-bound");
        assertTrue(normalized.contains("where s.user_id = ?"),
                "userId must be parameter-bound in the scope EXISTS predicate");
        // 本次新增锁定启用、明细权限和 ALL/精确 scope 过滤均保留在数据库层。
        assertTrue(normalized.contains("s.enabled = true"),
                "disabled scopes must not authorize detail rows");
        assertTrue(normalized.contains("s.can_view_detail = true"),
                "detail permission must be required");
        assertTrue(normalized.contains("s.scope_code = 'all' or s.scope_code = f.scope_code"),
                "scope must allow ALL or the exact fact scope");
    }

    @Test
    void mapsKeyFactFieldsFromRowMapper() throws Exception {
        // 本次新增逐项锁定维度、金额、预算、Wh 和材料损耗字段的 RowMapper 读取。
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("scope_code")).thenReturn("SCOPE-001");
        when(rs.getString("base_code")).thenReturn("BASE-001");
        when(rs.getString("product_code")).thenReturn("PRODUCT-001");
        when(rs.getString("line_code")).thenReturn("LINE-001");
        when(rs.getBigDecimal("volume")).thenReturn(decimal("1.10"));
        when(rs.getBigDecimal("revenue")).thenReturn(decimal("2.20"));
        when(rs.getBigDecimal("sales_cost")).thenReturn(decimal("3.30"));
        when(rs.getBigDecimal("budget_volume")).thenReturn(decimal("4.40"));
        when(rs.getBigDecimal("budget_revenue")).thenReturn(decimal("5.50"));
        when(rs.getBigDecimal("material_cost")).thenReturn(decimal("6.60"));
        when(rs.getBigDecimal("budget_material_cost")).thenReturn(decimal("7.70"));
        when(rs.getBigDecimal("output_wh")).thenReturn(decimal("8.80"));
        when(rs.getBigDecimal("budget_output_wh")).thenReturn(decimal("9.90"));
        when(rs.getBigDecimal("material_loss")).thenReturn(decimal("10.10"));
        when(rs.getBigDecimal("budget_material_loss")).thenReturn(decimal("11.11"));
        doAnswer(invocation -> {
            RowMapper<PnlFact> mapper = invocation.getArgument(1);
            return Collections.singletonList(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"), eq("U001"));

        List<PnlFact> actual = provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "U001");

        assertEquals(1, actual.size());
        PnlFact fact = actual.get(0);
        assertEquals("SCOPE-001", fact.getScopeCode());
        assertEquals("BASE-001", fact.getBaseCode());
        assertEquals("PRODUCT-001", fact.getProductCode());
        assertEquals("LINE-001", fact.getLineCode());
        assertEquals(decimal("1.10"), fact.getVolume());
        assertEquals(decimal("2.20"), fact.getRevenue());
        assertEquals(decimal("3.30"), fact.getSalesCost());
        assertEquals(decimal("4.40"), fact.getBudgetVolume());
        assertEquals(decimal("5.50"), fact.getBudgetRevenue());
        assertEquals(decimal("6.60"), fact.getMaterialCost());
        assertEquals(decimal("7.70"), fact.getBudgetMaterialCost());
        assertEquals(decimal("8.80"), fact.getOutputWh());
        assertEquals(decimal("9.90"), fact.getBudgetOutputWh());
        assertEquals(decimal("10.10"), fact.getMaterialLoss());
        assertEquals(decimal("11.11"), fact.getBudgetMaterialLoss());
    }

    @Test
    void returnsJdbcEmptyListAsEmptyResult() {
        // 本次新增锁定没有可见事实明细时返回 JdbcTemplate 的空列表，不抛异常也不改写结果。
        List<PnlFact> empty = Collections.emptyList();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("BATCH-EMPTY"), eq("BASE"), eq("U001")))
                .thenReturn(empty);

        List<PnlFact> actual = provider.findForUser("BATCH-EMPTY", AnalysisPerspective.BASE, "U001");

        assertSame(empty, actual);
        assertEquals(Collections.emptyList(), actual);
    }

    /**
     * 本次新增集中断言非法输入消息，并确认非法输入不会触发任何 JdbcTemplate 调用。
     */
    private void assertInvalid(String expectedMessage,
                               org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, action);
        assertEquals(expectedMessage, failure.getMessage());
        verifyNoInteractions(jdbc);
    }

    /**
     * 本次新增统一使用 BigDecimal 构造 RowMapper 断言值，避免浮点字面量引入精度歧义。
     */
    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
