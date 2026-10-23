package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.SourceFileValidationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 源文件版本值对象的边界与不变量契约。 */
class SourceFileVersionModelTest {
    @Test
    void createProducesPendingInactiveVersion() {
        SourceFileVersion version = SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1",
                "OBJ-1", 1, "TPL-1", "v1", "hash-1", "key-1", 100L, "u-1");

        assertNull(version.getId());
        assertEquals("SFV-1", version.getFileId());
        assertEquals(SourceFileValidationStatus.PENDING, version.getValidationStatus());
        assertFalse(version.isActive());
        assertEquals(1, version.getVersionNo());
        assertEquals(100L, version.getRowCount());
    }

    @Test
    void restoreRoundTripsAllFieldsIncludingActivation() {
        LocalDateTime uploaded = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        SourceFileVersion version = SourceFileVersion.restore(7L, "SFV-2", "B-2", "2026-10", "DS-2",
                "OBJ-2", 3, "TPL-2", "v3", "hash-2", "key-2", 200L,
                SourceFileValidationStatus.VALID, true, "u-2", uploaded);

        assertEquals(7L, version.getId());
        assertEquals("SFV-2", version.getFileId());
        assertEquals("OBJ-2", version.getObjectCode());
        assertEquals(3, version.getVersionNo());
        assertEquals(SourceFileValidationStatus.VALID, version.getValidationStatus());
        assertTrue(version.isActive());
        assertEquals("u-2", version.getUploadedBy());
        assertEquals(uploaded, version.getUploadedAt());
    }

    @Test
    void activateAndDeactivateFlipActiveFlag() {
        SourceFileVersion version = version();
        assertFalse(version.isActive());
        version.activate();
        assertTrue(version.isActive());
        version.deactivate();
        assertFalse(version.isActive());
    }

    @Test
    void markValidAndInvalidTransitionValidationStatus() {
        SourceFileVersion version = version();
        version.markValid();
        assertEquals(SourceFileValidationStatus.VALID, version.getValidationStatus());
        version.markInvalid();
        assertEquals(SourceFileValidationStatus.INVALID, version.getValidationStatus());
    }

    @Test
    void blankRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceFileVersion.create("", "B-1", "2026-10", "DS-1", "OBJ-1", 1,
                        "TPL-1", "v1", "hash-1", null, 0L, "u-1"));
        assertThrows(IllegalArgumentException.class,
                () -> SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1", " ", 1,
                        "TPL-1", "v1", "hash-1", null, 0L, "u-1"));
        assertThrows(IllegalArgumentException.class,
                () -> SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1", "OBJ-1", 1,
                        "TPL-1", "v1", null, null, 0L, "u-1"));
    }

    @Test
    void nonPositiveVersionAndNegativeRowCountAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1", "OBJ-1", 0,
                        "TPL-1", "v1", "hash-1", null, 0L, "u-1"));
        assertThrows(IllegalArgumentException.class,
                () -> SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1", "OBJ-1", 1,
                        "TPL-1", "v1", "hash-1", null, -1L, "u-1"));
    }

    private static SourceFileVersion version() {
        return SourceFileVersion.create("SFV-1", "B-1", "2026-10", "DS-1", "OBJ-1", 1,
                "TPL-1", "v1", "hash-1", null, 0L, "u-1");
    }
}
