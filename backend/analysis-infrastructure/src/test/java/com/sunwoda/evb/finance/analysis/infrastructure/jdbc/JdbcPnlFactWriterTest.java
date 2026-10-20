package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPnlFactWriterTest {
    private JdbcTemplate jdbc;
    private JdbcPnlFactWriter writer;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        writer = new JdbcPnlFactWriter(mock(DataSource.class));
        Field field = JdbcPnlFactWriter.class.getDeclaredField("jdbc");
        field.setAccessible(true);
        field.set(writer, jdbc);
    }

    @Test
    void looksUpBatchBeforeDeletingAndInsertingFacts() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        List<PnlFact> facts = Arrays.asList(fact("SCOPE-001"), fact("SCOPE-002"));
        List<String> updateSqls = new ArrayList<>();
        recordUpdates(updateSqls, null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, facts);

        verify(jdbc).queryForObject(contains("meta.analysis_batch"), eq(Long.class),
                eq("BATCH-001"));
        assertEquals(3, updateSqls.size());
        assertTrue(updateSqls.get(0).contains("DELETE FROM raw.pnl_fact"));
        assertTrue(updateSqls.get(1).contains("INSERT INTO raw.pnl_fact"));
        assertTrue(updateSqls.get(2).contains("INSERT INTO raw.pnl_fact"));
    }

    @Test
    void bindsNullableDimensionsAndAllFactValuesToEachInsert() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.PRODUCT_LINE);
        ImportTask task = task("TASK-001", "BATCH-001");
        PnlFact fact = dimensionedFactWithNullableFields();
        List<Object[]> insertArguments = new ArrayList<>();
        recordUpdates(new ArrayList<>(), insertArguments);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, Collections.singletonList(fact));

        assertEquals(1, insertArguments.size());
        Object[] values = insertArguments.get(0);
        assertEquals(40, values.length);
        assertEquals(41L, values[0]);
        assertEquals("PRODUCT_LINE", values[1]);
        assertEquals("TASK-001", values[2]);
        assertEquals("2026-01", values[3]);
        assertEquals("SCOPE-001", values[4]);
        assertNull(values[5]);
        assertEquals("PRODUCT-001", values[6]);
        assertNull(values[7]);
        for (int index = 8; index < 36; index++) {
            assertEquals(decimal(index - 7), values[index]);
        }
        assertNull(values[36]);
        assertNull(values[37]);
        assertNull(values[38]);
        assertNull(values[39]);
    }

    @Test
    void bindsWhAndMaterialLossExtensionsInTheirDeclaredColumnOrder() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        PnlFact fact = factWithExtensions();
        List<Object[]> insertArguments = new ArrayList<>();
        recordUpdates(new ArrayList<>(), insertArguments);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, Collections.singletonList(fact));

        Object[] values = insertArguments.get(0);
        assertEquals(decimal(29), values[36]);
        assertEquals(decimal(30), values[37]);
        assertEquals(decimal(31), values[38]);
        assertEquals(decimal(32), values[39]);
    }

    @Test
    void emptyReplacementDeletesWithoutInserting() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BUSINESS_UNIT);
        ImportTask task = task("TASK-001", "BATCH-001");
        List<String> updateSqls = new ArrayList<>();
        recordUpdates(updateSqls, null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        writer.replace(batch, task, Collections.<PnlFact>emptyList());

        assertEquals(1, updateSqls.size());
        assertTrue(updateSqls.get(0).contains("DELETE FROM raw.pnl_fact"));
    }

    @Test
    void missingBatchStopsBeforeDeleteAndInsert() {
        AnalysisBatch batch = batch("BATCH-MISSING", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-MISSING");
        List<String> updateSqls = new ArrayList<>();
        recordUpdates(updateSqls, null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-MISSING")))
                .thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> writer.replace(batch, task, Collections.singletonList(fact("SCOPE-001"))));

        assertEquals("批次不存在: BATCH-MISSING", exception.getMessage());
        assertTrue(updateSqls.isEmpty());
    }

    @Test
    void nullBatchFailsBeforeJdbcLookup() {
        assertThrows(NullPointerException.class,
                () -> writer.replace(null, task("TASK-001", "BATCH-001"),
                        Collections.<PnlFact>emptyList()));

        verify(jdbc, never()).queryForObject(anyString(), eq(Long.class), any());
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void nullTaskFailsAfterBatchLookupBeforeDelete() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        assertThrows(NullPointerException.class,
                () -> writer.replace(batch, null, Collections.<PnlFact>emptyList()));

        verify(jdbc).queryForObject(anyString(), eq(Long.class), eq("BATCH-001"));
        verify(jdbc, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void nullFactsFailsAfterDeleteWithoutInsert() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        List<String> updateSqls = new ArrayList<>();
        recordUpdates(updateSqls, null);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("BATCH-001")))
                .thenReturn(41L);

        assertThrows(NullPointerException.class, () -> writer.replace(batch, task, null));

        assertEquals(1, updateSqls.size());
        assertTrue(updateSqls.get(0).contains("DELETE FROM raw.pnl_fact"));
    }

    private void recordUpdates(List<String> updateSqls, List<Object[]> insertArguments) {
        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String sql = (String) arguments[0];
            updateSqls.add(sql);
            if (insertArguments != null && sql.contains("INSERT INTO raw.pnl_fact")) {
                insertArguments.add(Arrays.copyOfRange(arguments, 1, arguments.length));
            }
            return 1;
        }).when(jdbc).update(anyString(), (Object[]) any());
    }

    private AnalysisBatch batch(String batchNo, AnalysisPerspective perspective) {
        return new AnalysisBatch(1L, batchNo, "2026-01", perspective,
                BatchStatus.DRAFT, "tester", LocalDateTime.now());
    }

    private ImportTask task(String taskNo, String batchNo) {
        return new ImportTask(1L, taskNo, batchNo, "DATASET", "facts.xlsx",
                "checksum", "tester", LocalDateTime.now());
    }

    private PnlFact fact(String scopeCode) {
        return new PnlFact(scopeCode, "BASE-001", "PRODUCT-001", "LINE-001",
                decimal(1), decimal(2), decimal(3), decimal(4), decimal(5), decimal(6),
                decimal(7), decimal(8), decimal(9), decimal(10), decimal(11), decimal(12),
                decimal(13), decimal(14), decimal(15), decimal(16), decimal(17), decimal(18),
                decimal(19), decimal(20), decimal(21), decimal(22), decimal(23), decimal(24),
                decimal(25), decimal(26), decimal(27), decimal(28));
    }

    private PnlFact dimensionedFactWithNullableFields() {
        return new PnlFact("SCOPE-001", null, " PRODUCT-001 ", null,
                decimal(1), decimal(2), decimal(3), decimal(4), decimal(5), decimal(6),
                decimal(7), decimal(8), decimal(9), decimal(10), decimal(11), decimal(12),
                decimal(13), decimal(14), decimal(15), decimal(16), decimal(17), decimal(18),
                decimal(19), decimal(20), decimal(21), decimal(22), decimal(23), decimal(24),
                decimal(25), decimal(26), decimal(27), decimal(28), null, null, null, null);
    }

    private PnlFact factWithExtensions() {
        return new PnlFact("SCOPE-001", "BASE-001", "PRODUCT-001", "LINE-001",
                decimal(1), decimal(2), decimal(3), decimal(4), decimal(5), decimal(6),
                decimal(7), decimal(8), decimal(9), decimal(10), decimal(11), decimal(12),
                decimal(13), decimal(14), decimal(15), decimal(16), decimal(17), decimal(18),
                decimal(19), decimal(20), decimal(21), decimal(22), decimal(23), decimal(24),
                decimal(25), decimal(26), decimal(27), decimal(28), decimal(29), decimal(30),
                decimal(31), decimal(32));
    }

    private BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value);
    }
}
