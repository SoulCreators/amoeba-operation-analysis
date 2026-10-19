package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryPnlFactSourceWriterTest {
    private final InMemoryPnlFactSourceWriter writer = new InMemoryPnlFactSourceWriter();

    @Test
    void shouldWriteAndQuerySourcesByBatchPerspectiveAndTask() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        List<PnlFactSource> sources = Arrays.asList(source("TASK-001", "Sheet1", 2),
                source("TASK-001", "Sheet1", 3));

        writer.replace(batch, task, sources);

        assertEquals(sources, writer.findByBatchNoAndPerspectiveAndTaskNo(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001"));
        assertEquals(Collections.emptyList(), writer.findByBatchNoAndPerspectiveAndTaskNo(
                "BATCH-001", AnalysisPerspective.PRODUCT_LINE, "TASK-001"));
    }

    @Test
    void shouldReplaceAndRemoveOldRows() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        writer.replace(batch, task, Arrays.asList(source("TASK-001", "Sheet1", 2),
                source("TASK-001", "Sheet1", 3)));

        PnlFactSource latest = source("TASK-001", "Sheet2", 8);
        writer.replace(batch, task, Collections.singletonList(latest));

        assertEquals(Collections.singletonList(latest), writer.findByBatchNoAndPerspectiveAndTaskNo(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001"));
    }

    @Test
    void shouldRejectTaskFromAnotherBatch() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-002");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task, Collections.<PnlFactSource>emptyList()));
    }

    @Test
    void shouldRejectSourceWithAnotherTaskNo() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task,
                        Collections.singletonList(source("TASK-002", "Sheet1", 2))));
    }

    @Test
    void shouldRejectDuplicateSheetAndRow() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");

        assertThrows(IllegalArgumentException.class,
                () -> writer.replace(batch, task, Arrays.asList(
                        source("TASK-001", "Sheet1", 2),
                        source("TASK-001", "Sheet1", 2))));
    }

    @Test
    void shouldClearCurrentRowsWhenReplacingWithEmptyList() {
        AnalysisBatch batch = batch("BATCH-001", AnalysisPerspective.BASE);
        ImportTask task = task("TASK-001", "BATCH-001");
        writer.replace(batch, task, Collections.singletonList(source("TASK-001", "Sheet1", 2)));

        writer.replace(batch, task, Collections.<PnlFactSource>emptyList());

        assertEquals(Collections.emptyList(), writer.findByBatchNoAndPerspectiveAndTaskNo(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001"));
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
