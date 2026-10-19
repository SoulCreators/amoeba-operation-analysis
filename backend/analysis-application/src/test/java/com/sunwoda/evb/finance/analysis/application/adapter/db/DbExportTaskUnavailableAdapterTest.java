package com.sunwoda.evb.finance.analysis.application.adapter.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbExportTaskUnavailableAdapterTest {
    @Test
    void failsLoudlyWhenDbProfileHasNoProductionExportPort() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new DbExportTaskUnavailableAdapter().create(
                        "U001", "REQ-001", "B-001-R1.csv", "text/csv"));

        assertTrue(failure.getMessage().contains("no production ExportTaskPort"));
    }
}
