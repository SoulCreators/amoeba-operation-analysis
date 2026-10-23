package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublicationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.PairingStatus;
import com.sunwoda.evb.finance.analysis.domain.model.TargetActual;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 非财经指标值对象（事实行 / 配对结果）的边界与不变量契约。 */
class NonfinancialModelTest {
    @Test
    void itemProducesUnboundFactAndBindFillsPeriod() {
        NonfinancialFact item = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.TARGET, new BigDecimal("0.9800"), 7, "fv-1");

        assertNull(item.getPeriod());
        assertEquals("BASE-1", item.getBaseCode());
        assertEquals("CELL-1", item.getCellModel());
        assertEquals(TargetActual.TARGET, item.getTargetActual());
        assertEquals(0, item.getQualityValue().compareTo(new BigDecimal("0.9800")));
        assertEquals(7, item.getSourceRowNo());

        NonfinancialFact bound = item.bind("2026-10");
        assertEquals("2026-10", bound.getPeriod());
        assertEquals("BASE-1", bound.getBaseCode());
    }

    @Test
    void businessKeyExcludesTargetActualSoTargetAndActualPair() {
        NonfinancialFact target = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.TARGET, new BigDecimal("1.0"), 1, "fv-1").bind("2026-10");
        NonfinancialFact actual = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.ACTUAL, new BigDecimal("0.9"), 2, "fv-1").bind("2026-10");

        assertEquals(target.businessKey(), actual.businessKey());
        assertEquals(target.businessKey(), actual.businessKey());
    }

    @Test
    void distinctBusinessKeyForDifferentDimensions() {
        NonfinancialFact a = NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                TargetActual.TARGET, new BigDecimal("1.0"), 1, "fv-1").bind("2026-10");
        NonfinancialFact b = NonfinancialFact.item("BASE-1", "L1", "CELL-2", "YIELD",
                TargetActual.TARGET, new BigDecimal("1.0"), 1, "fv-1").bind("2026-10");
        assertNotEquals(a.businessKey(), b.businessKey());
    }

    @Test
    void blankRequiredFactFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, new BigDecimal("1.0"), 1, "fv-1"));
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("BASE-1", "L1", " ", "YIELD",
                        TargetActual.TARGET, new BigDecimal("1.0"), 1, "fv-1"));
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, new BigDecimal("1.0"), 1, null));
    }

    @Test
    void nullTargetActualValueAndNegativeRowNoAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                        null, new BigDecimal("1.0"), 1, "fv-1"));
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, null, 1, "fv-1"));
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialFact.item("BASE-1", "L1", "CELL-1", "YIELD",
                        TargetActual.TARGET, new BigDecimal("1.0"), -1, "fv-1"));
    }

    @Test
    void resultRoundTripsAllFields() {
        NonfinancialResult result = new NonfinancialResult(9L, "2026-10", "BASE-1", "L1",
                "CELL-1", "YIELD", new BigDecimal("1.00"), new BigDecimal("0.90"),
                new BigDecimal("-0.10"), new BigDecimal("0.9000000000"), PairingStatus.BOTH,
                "LDR-1", false);

        assertEquals(9L, result.getId());
        assertEquals("2026-10", result.getPeriod());
        assertEquals("BASE-1", result.getBaseCode());
        assertEquals(PairingStatus.BOTH, result.getPairingStatus());
        assertEquals("LDR-1", result.getLeaderEmployeeNo());
        assertFalse(result.isLeaderConflict());
    }

    @Test
    void resultRejectsNullPairingStatusAndBlankDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new NonfinancialResult(1L, "2026-10", "BASE-1", "L1", "CELL-1", "YIELD",
                        null, null, null, null, null, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NonfinancialResult(1L, " ", "BASE-1", "L1", "CELL-1", "YIELD",
                        null, null, null, null, PairingStatus.BOTH, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new NonfinancialResult(1L, "2026-10", "", "L1", "CELL-1", "YIELD",
                        null, null, null, null, PairingStatus.BOTH, null, false));
    }

    @Test
    void conflictFlagIsPreserved() {
        NonfinancialResult conflict = new NonfinancialResult(2L, "2026-10", "BASE-1", "L1",
                "CELL-1", "YIELD", null, null, null, null, PairingStatus.ACTUAL_ONLY,
                null, true);
        assertTrue(conflict.isLeaderConflict());
    }

    @Test
    void publicationDraftIsNotPublished() {
        NonfinancialPublication draft = NonfinancialPublication.draft("2026-10");
        assertEquals("2026-10", draft.getPeriod());
        assertEquals(NonfinancialPublicationStatus.DRAFT, draft.getStatus());
        assertFalse(draft.isPublished());
        assertNull(draft.getPublishedBy());
        assertNull(draft.getPublishedAt());
    }

    @Test
    void publicationPublishRequiresOperatorAndTimestamp() {
        NonfinancialPublication draft = NonfinancialPublication.draft("2026-10");
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        NonfinancialPublication published = draft.publish(0, "hq-finance", now);

        assertTrue(published.isPublished());
        assertEquals(0, published.getConflictCount());
        assertEquals("hq-finance", published.getPublishedBy());
        assertEquals(now, published.getPublishedAt());

        assertThrows(IllegalArgumentException.class, () -> draft.publish(0, " ", now));
        assertThrows(IllegalArgumentException.class, () -> draft.publish(0, "hq-finance", null));
    }

    @Test
    void publicationRejectsBlankPeriodAndNegativeConflict() {
        assertThrows(IllegalArgumentException.class,
                () -> NonfinancialPublication.draft(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new NonfinancialPublication("2026-10", NonfinancialPublicationStatus.DRAFT,
                        -1, null, null));
    }
}
