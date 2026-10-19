package com.sunwoda.evb.finance.analysis.application.model;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportTaskSnapshotTest {
    @Test
    void copiesAndNormalizesAuthorizedScopes() {
        Set<String> summary = new LinkedHashSet<String>();
        summary.add(" nanchang ");
        Set<String> detail = new LinkedHashSet<String>();
        detail.add("WUHAN");

        ExportTaskSnapshot snapshot = new ExportTaskSnapshot("B-001", "R3",
                AnalysisPerspective.BASE, CalculationResultStatus.PUBLISHED,
                ExportFormat.XLSX, summary, detail);
        summary.add("MUTATED");
        detail.clear();

        assertEquals("B-001-R3.xlsx", snapshot.fileName());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                snapshot.contentType());
        assertEquals(Collections.singleton("NANCHANG"),
                snapshot.getSummaryScopes());
        assertEquals(Collections.singleton("WUHAN"),
                snapshot.getDetailScopes());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getSummaryScopes().add("OTHER"));
    }

    @Test
    void rejectsMissingSnapshotIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExportTaskSnapshot(" ", "R1", AnalysisPerspective.BASE,
                        CalculationResultStatus.PUBLISHED, ExportFormat.CSV,
                        null, null));
        assertTrue(new ExportTaskSnapshot("B", "R1", AnalysisPerspective.BASE,
                CalculationResultStatus.PUBLISHED, ExportFormat.CSV, null, null)
                .getSummaryScopes().isEmpty());
    }
}
