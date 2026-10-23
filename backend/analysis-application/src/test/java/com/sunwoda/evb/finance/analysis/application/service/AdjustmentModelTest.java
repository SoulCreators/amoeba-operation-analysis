package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 简易调整单值对象的状态机与不变量契约。 */
class AdjustmentModelTest {
    @Test
    void createDefaultsToDraftAndTrimsFields() {
        Adjustment draft = Adjustment.create("  ADJ-1  ", "  2026-09 ", AnalysisPerspective.BASE,
                "  NANCHANG ", AdjustmentScenario.ACTUAL, " REVENUE ",
                new BigDecimal("100.00"), AdjustmentDirection.INCREASE, " 口径补充 ",
                " ev-1 ", " R1 ", " U001 ", LocalDateTime.now());

        assertEquals("ADJ-1", draft.getAdjustmentId());
        assertEquals("2026-09", draft.getPeriod());
        assertEquals("NANCHANG", draft.getScopeCode());
        assertEquals("REVENUE", draft.getMetricCode());
        assertEquals("口径补充", draft.getReason());
        assertEquals("ev-1", draft.getEvidenceFileId());
        assertEquals("R1", draft.getExpectedVersion());
        assertEquals("U001", draft.getEnteredBy());
        assertEquals(AdjustmentStatus.DRAFT, draft.getStatus());
        assertEquals(AdjustmentDirection.INCREASE, draft.getDirection());
        assertEquals(new BigDecimal("100.00"), draft.getAmount());
    }

    @Test
    void activateTransitionsDraftToActive() {
        Adjustment draft = draft();
        Adjustment active = draft.activate();
        assertEquals(AdjustmentStatus.ACTIVE, active.getStatus());
        assertEquals(AdjustmentStatus.DRAFT, draft.getStatus());
    }

    @Test
    void activateRejectsNonDraft() {
        Adjustment active = draft().activate();
        IllegalStateException error = assertThrows(IllegalStateException.class, active::activate);
        assertEquals("只有草稿调整可以生效: ACTIVE", error.getMessage());
    }

    @Test
    void voidAdjustmentTransitionsActiveToVoidWithTrace() {
        Adjustment active = draft().activate();
        Adjustment voided = active.voidAdjustment(" U002 ", LocalDateTime.now(), " 政策变更 ");

        assertEquals(AdjustmentStatus.VOID, voided.getStatus());
        assertEquals("U002", voided.getVoidedBy());
        assertEquals("政策变更", voided.getVoidReason());
        assertEquals(AdjustmentStatus.ACTIVE, active.getStatus());
    }

    @Test
    void voidAdjustmentRejectsNonActive() {
        Adjustment draft = draft();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> draft.voidAdjustment("U002", LocalDateTime.now(), "政策变更"));
        assertEquals("只有已生效调整可以作废: DRAFT", error.getMessage());
    }

    @Test
    void signedAmountNegatesDecreaseAndKeepsIncrease() {
        Adjustment increase = draft(AdjustmentDirection.INCREASE, "100.00");
        Adjustment decrease = draft(AdjustmentDirection.DECREASE, "100.00");
        assertEquals(new BigDecimal("100.00"), increase.signedAmount());
        assertEquals(new BigDecimal("-100.00"), decrease.signedAmount());
    }

    @Test
    void createRejectsNegativeAmount() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> draft(AdjustmentDirection.INCREASE, "-1.00"));
        assertEquals("调整金额必须为非负数，方向由 direction 表达", error.getMessage());
    }

    @Test
    void createRejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> Adjustment.create("ADJ-1", "  ", AnalysisPerspective.BASE, "NANCHANG",
                        AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("1"),
                        AdjustmentDirection.INCREASE, "reason", null, null, "U001", null));
        assertThrows(IllegalArgumentException.class,
                () -> Adjustment.create("ADJ-1", "2026-09", AnalysisPerspective.BASE, "NANCHANG",
                        AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("1"),
                        AdjustmentDirection.INCREASE, "  ", null, null, "U001", null));
    }

    private static Adjustment draft() {
        return draft(AdjustmentDirection.INCREASE, "100.00");
    }

    private static Adjustment draft(AdjustmentDirection direction, String amount) {
        return Adjustment.create("ADJ-1", "2026-09", AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal(amount), direction,
                "口径补充", "ev-1", "R1", "U001", LocalDateTime.now());
    }
}
