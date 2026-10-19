package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PnlFactSourceTest {
    @Test
    void shouldCreateValidImmutableSourceContract() {
        PnlFactSource source = new PnlFactSource(
                " TASK-001 ", " pnl.xlsx ", " sha256:declared ", " Sheet1 ", 1,
                " BU-001 ", " BASE-001 ", " PRODUCT-001 ", " LINE-001 ");

        assertEquals("TASK-001", source.getTaskNo());
        assertEquals("pnl.xlsx", source.getFileName());
        assertEquals(" sha256:declared ", source.getChecksum());
        assertEquals("Sheet1", source.getSheetName());
        assertEquals(1, source.getRowNo());
        assertEquals("BU-001", source.getScopeCode());
        assertEquals("BASE-001", source.getBaseCode());
        assertEquals("PRODUCT-001", source.getProductCode());
        assertEquals("LINE-001", source.getLineCode());
    }

    @Test
    void shouldAcceptExcelOneBasedRowBoundary() {
        PnlFactSource source = sourceWithRow(1);

        assertEquals(1, source.getRowNo());
        assertThrows(IllegalArgumentException.class, () -> sourceWithRow(0));
        assertThrows(IllegalArgumentException.class, () -> sourceWithRow(-1));
    }

    @Test
    void shouldRejectBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> source(null, "file.xlsx", "Sheet1", "SCOPE"));
        assertThrows(IllegalArgumentException.class, () -> source("TASK", " ", "Sheet1", "SCOPE"));
        assertThrows(IllegalArgumentException.class, () -> source("TASK", "file.xlsx", "\t", "SCOPE"));
        assertThrows(IllegalArgumentException.class, () -> source("TASK", "file.xlsx", "Sheet1", " "));
    }

    @Test
    void shouldPreserveEmptyChecksumAsProvided() {
        assertEquals("", sourceWithChecksum("").getChecksum());
        assertNull(sourceWithChecksum(null).getChecksum());
        assertEquals("  declared  ", sourceWithChecksum("  declared  ").getChecksum());
    }

    @Test
    void shouldNormalizeBlankOptionalDimensionsToNull() {
        PnlFactSource source = new PnlFactSource(
                "TASK", "file.xlsx", "checksum", "Sheet1", 2,
                "SCOPE", "  ", "\t", null);

        assertNull(source.getBaseCode());
        assertNull(source.getProductCode());
        assertNull(source.getLineCode());
    }

    private PnlFactSource sourceWithRow(int rowNo) {
        return new PnlFactSource("TASK", "file.xlsx", null, "Sheet1", rowNo,
                "SCOPE", null, null, null);
    }

    private PnlFactSource source(String taskNo, String fileName, String sheetName, String scopeCode) {
        return new PnlFactSource(taskNo, fileName, null, sheetName, 1,
                scopeCode, null, null, null);
    }

    private PnlFactSource sourceWithChecksum(String checksum) {
        return new PnlFactSource("TASK", "file.xlsx", checksum, "Sheet1", 1,
                "SCOPE", null, null, null);
    }
}
