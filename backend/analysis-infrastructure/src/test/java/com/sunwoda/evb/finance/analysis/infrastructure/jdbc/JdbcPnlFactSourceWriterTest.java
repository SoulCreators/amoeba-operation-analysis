package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPnlFactSourceWriterTest {
    private JdbcTemplate jdbc;
    private JdbcPnlFactSourceWriter writer;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        writer = new JdbcPnlFactSourceWriter(jdbc);
    }

    @Test
    void rejectsCrossBatchTaskBeforeTouchingJdbc() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-002");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task, Collections.<PnlFactSource>emptyList()));

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void rejectsSourceFromAnotherTaskBeforeTouchingJdbc() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task,
                        Collections.singletonList(source("TASK-002", "Sheet1", 2))));

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void rejectsDuplicateSheetAndRowBeforeTouchingJdbc() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task, Arrays.asList(
                        source("TASK-001", "Sheet1", 2),
                        source("TASK-001", "Sheet1", 2))));

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
    }

    @Test
    void replacesRowsWithDeleteAndBatchInsert() throws Exception {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        List<PnlFactSource> sources = Arrays.asList(
                source("TASK-001", "Sheet1", 2), source("TASK-001", "Sheet1", 3));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, sources);

        verify(jdbc).queryForObject(anyString(), eq(Long.class), eq("BATCH-001"));
        verify(jdbc).update(anyString(), eq(41L), eq("BASE"), eq("TASK-001"));
        verify(jdbc).batchUpdate(anyString(), eq(sources), eq(2),
                any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void bindsNullableAndDimensionParametersForBatchInsert() throws Exception {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        PnlFactSource source = new PnlFactSource("TASK-001", "source.xlsx", null,
                "Sheet1", 2, "SCOPE-001", null, "PRODUCT-001", null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);
        doAnswer(invocation -> {
            ParameterizedPreparedStatementSetter<PnlFactSource> setter = invocation.getArgument(3);
            PreparedStatement ps = mock(PreparedStatement.class);
            setter.setValues(ps, source);
            verify(ps).setLong(1, 41L);
            verify(ps).setString(2, "BASE");
            verify(ps).setString(3, "TASK-001");
            verify(ps).setString(4, "source.xlsx");
            verify(ps).setNull(5, java.sql.Types.VARCHAR);
            verify(ps).setString(6, "Sheet1");
            verify(ps).setInt(7, 2);
            verify(ps).setString(8, "SCOPE-001");
            verify(ps).setNull(9, java.sql.Types.VARCHAR);
            verify(ps).setString(10, "PRODUCT-001");
            verify(ps).setNull(11, java.sql.Types.VARCHAR);
            return null;
        }).when(jdbc).batchUpdate(anyString(), eq(Collections.singletonList(source)), eq(1),
                any(ParameterizedPreparedStatementSetter.class));

        writer.replace(batch, task, Collections.singletonList(source));
    }

    @Test
    void emptyReplaceDeletesWithoutInsert() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.PRODUCT_LINE);
        ImportTask task = task("TASK-001", "BATCH-001");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, Collections.<PnlFactSource>emptyList());

        verify(jdbc).update(anyString(), eq(41L), eq("PRODUCT_LINE"), eq("TASK-001"));
        verify(jdbc, never()).batchUpdate(anyString(), any(List.class), anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
    }

    @Test
    void mapsQueryRowsToSourcesAndUsesStableOrderingSql() throws Exception {
        AnalysisPerspective perspective = AnalysisPerspective.BUSINESS_UNIT;
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("source_task_no")).thenReturn("TASK-001");
        when(rs.getString("source_file_name")).thenReturn("source.xlsx");
        when(rs.getString("source_checksum")).thenReturn("sha256:value");
        when(rs.getString("source_sheet_name")).thenReturn("Sheet1");
        when(rs.getInt("source_row_no")).thenReturn(7);
        when(rs.getString("scope_code")).thenReturn("SCOPE-001");
        when(rs.getString("base_code")).thenReturn("BASE-001");
        when(rs.getString("product_code")).thenReturn("PRODUCT-001");
        when(rs.getString("line_code")).thenReturn("LINE-001");
        doAnswer(invocation -> {
            org.springframework.jdbc.core.RowMapper<PnlFactSource> mapper = invocation.getArgument(1);
            return Collections.singletonList(mapper.mapRow(rs, 0));
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-001"), eq("BUSINESS_UNIT"), eq("TASK-001"));

        List<PnlFactSource> result = writer.findByBatchNoAndPerspectiveAndTaskNo(
                "BATCH-001", perspective, "TASK-001");

        assertEquals(1, result.size());
        assertEquals("TASK-001", result.get(0).getTaskNo());
        assertEquals("Sheet1", result.get(0).getSheetName());
        assertEquals(7, result.get(0).getRowNo());
        verify(jdbc).query(org.mockito.ArgumentMatchers.contains("ORDER BY t.id, t.source_sheet_name, t.source_row_no"),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq("BATCH-001"), eq("BUSINESS_UNIT"), eq("TASK-001"));
    }

    private AnalysisBatch batch(String batchNo, AnalysisPerspective perspective) {
        return new AnalysisBatch(1L, batchNo, "2026-01", perspective,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private ImportTask task(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "DATASET", "source.xlsx",
                "checksum", "tester", LocalDateTime.now());
    }

    private PnlFactSource source(String taskNo, String sheetName, int rowNo) {
        return new PnlFactSource(taskNo, "source.xlsx", "checksum", sheetName, rowNo,
                "SCOPE-001", "BASE-001", "PRODUCT-001", "LINE-001");
    }
}
