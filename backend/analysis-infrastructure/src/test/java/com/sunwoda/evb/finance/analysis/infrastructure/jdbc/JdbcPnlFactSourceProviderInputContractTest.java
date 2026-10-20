package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
 * TRACE-JDBC-PG-SEC-001：锁定 JdbcPnlFactSourceProvider 的输入与结果边界。
 *
 * <p>本测试只使用 Mockito/JdbcTemplate，不连接真实数据库，专门覆盖输入校验、
 * trim 后参数绑定、9 个追溯字段映射以及 JDBC 空结果语义。</p>
 */
class JdbcPnlFactSourceProviderInputContractTest {
    private JdbcTemplate jdbc;
    private JdbcPnlFactSourceProvider provider;

    @BeforeEach
    void setUp() {
        // 本次修改通过 mock JdbcTemplate 隔离数据库，确保契约只验证 Provider 边界行为。
        jdbc = mock(JdbcTemplate.class);
        provider = new JdbcPnlFactSourceProvider(jdbc);
    }

    @Test
    void rejectsNullOrBlankBatchNoBeforeTouchingJdbc() {
        // 本次修改锁定 batchNo 对 null、空串和全空白输入均拒绝且不访问 JDBC。
        assertInvalid("batchNo must not be blank",
                () -> provider.findForUser(null, AnalysisPerspective.BASE, "TASK-001", "U001"));
        assertInvalid("batchNo must not be blank",
                () -> provider.findForUser(" \t", AnalysisPerspective.BASE, "TASK-001", "U001"));
    }

    @Test
    void rejectsNullPerspectiveBeforeTouchingJdbc() {
        // 本次修改锁定 perspective 缺失时的当前异常语义，并确认校验短路在 JDBC 之前。
        assertInvalid("perspective must not be null",
                () -> provider.findForUser("BATCH-001", null, "TASK-001", "U001"));
    }

    @Test
    void rejectsNullOrBlankTaskNoBeforeTouchingJdbc() {
        // 本次修改锁定 taskNo 对 null 和全空白输入的当前校验行为。
        assertInvalid("taskNo must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, null, "U001"));
        assertInvalid("taskNo must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "\n", "U001"));
    }

    @Test
    void rejectsNullOrBlankUserIdBeforeTouchingJdbc() {
        // 本次修改锁定 userId 对 null 和全空白输入的当前校验行为。
        assertInvalid("userId must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", null));
        assertInvalid("userId must not be blank",
                () -> provider.findForUser("BATCH-001", AnalysisPerspective.BASE, "TASK-001", "  "));
    }

    @Test
    void trimsStringInputsBeforeBindingJdbcParameters() {
        // 本次修改锁定 SQL 使用 t.perspective = ?，并锁定 batch/perspective/task/user 四参数顺序。
        List<PnlFactSource> expected = Collections.emptyList();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"),
                eq("TASK-001"), eq("U001"))).thenReturn(expected);

        List<PnlFactSource> actual = provider.findForUser(
                "  BATCH-001  ", AnalysisPerspective.BASE, "\tTASK-001\n", " U001 ");

        assertSame(expected, actual);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"),
                eq("TASK-001"), eq("U001"));
        // 本次修改明确断言 perspective 也是占位符，防止回退为字符串拼接或漏掉视角过滤。
        assertTrue(sql.getValue().toLowerCase(Locale.ROOT).contains("t.perspective = ?"),
                "perspective must be parameter-bound");
    }

    @Test
    void mapsAllNineTraceFieldsFromRowMapper() throws Exception {
        // 本次修改逐字段锁定 RowMapper 对 9 个追溯列的映射，防止列名或字段顺序漂移。
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("source_task_no")).thenReturn("TASK-ROW-001");
        when(rs.getString("source_file_name")).thenReturn("pnl.xlsx");
        when(rs.getString("source_checksum")).thenReturn("sha256:abc");
        when(rs.getString("source_sheet_name")).thenReturn("Sheet-1");
        when(rs.getInt("source_row_no")).thenReturn(27);
        when(rs.getString("scope_code")).thenReturn("SCOPE-001");
        when(rs.getString("base_code")).thenReturn("BASE-001");
        when(rs.getString("product_code")).thenReturn("PRODUCT-001");
        when(rs.getString("line_code")).thenReturn("LINE-001");
        doAnswer(invocation -> {
            RowMapper<PnlFactSource> mapper = invocation.getArgument(1);
            return Collections.singletonList(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq("BATCH-001"), eq("BASE"),
                eq("TASK-001"), eq("U001"));

        List<PnlFactSource> actual = provider.findForUser(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001", "U001");

        assertEquals(1, actual.size());
        PnlFactSource source = actual.get(0);
        assertEquals("TASK-ROW-001", source.getTaskNo());
        assertEquals("pnl.xlsx", source.getFileName());
        assertEquals("sha256:abc", source.getChecksum());
        assertEquals("Sheet-1", source.getSheetName());
        assertEquals(27, source.getRowNo());
        assertEquals("SCOPE-001", source.getScopeCode());
        assertEquals("BASE-001", source.getBaseCode());
        assertEquals("PRODUCT-001", source.getProductCode());
        assertEquals("LINE-001", source.getLineCode());
    }

    @Test
    void returnsJdbcEmptyListAsEmptyResult() {
        // 本次修改锁定无可见追溯记录时返回 JdbcTemplate 的空列表，不抛异常也不改写语义。
        List<PnlFactSource> empty = Collections.emptyList();
        when(jdbc.query(anyString(), any(RowMapper.class), eq("BATCH-EMPTY"), eq("BASE"),
                eq("TASK-EMPTY"), eq("U001"))).thenReturn(empty);

        List<PnlFactSource> actual = provider.findForUser(
                "BATCH-EMPTY", AnalysisPerspective.BASE, "TASK-EMPTY", "U001");

        assertSame(empty, actual);
        assertEquals(Collections.emptyList(), actual);
    }

    /**
     * 本次修改集中断言当前非法输入消息，并确认非法输入不会触发任何 JdbcTemplate 调用。
     */
    private void assertInvalid(String expectedMessage, org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, action);
        assertEquals(expectedMessage, failure.getMessage());
        verifyNoInteractions(jdbc);
    }
}
