package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 异常工作台工作项的边界与不变量契约。 */
class ExceptionWorkItemModelTest {
    @Test
    void reportCreatesOpenItemWithoutIgnoreTrace() {
        ExceptionWorkItem item = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, 3, "金额", "金额为空", "");

        assertNull(item.getId());
        assertEquals("T-1", item.getTaskNo());
        assertEquals(IssueStatus.OPEN, item.getStatus());
        assertFalse(item.isBlocking());
        assertTrue(item.isOpen());
        assertNull(item.getIgnoredBy());
        assertNull(item.getIgnoredAt());
        assertNull(item.getIgnoreReason());
    }

    @Test
    void ignoreWarningRecordsPersonTimeAndReason() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        ExceptionWorkItem item = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, 3, "金额", "金额为空", "").ignore("历史空值可忽略", "finance", at);

        assertEquals(IssueStatus.IGNORED, item.getStatus());
        assertTrue(item.isIgnored());
        assertEquals("finance", item.getIgnoredBy());
        assertEquals(at, item.getIgnoredAt());
        assertEquals("历史空值可忽略", item.getIgnoreReason());
    }

    @Test
    void blockingItemCannotBeIgnored() {
        ExceptionWorkItem item = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.BLOCKING, 3, "金额", "金额为空", "");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> item.ignore("原因", "finance", LocalDateTime.now()));
        assertEquals("阻断项不可忽略: FIELD_EMPTY", ex.getMessage());
    }

    @Test
    void alreadyIgnoredItemCannotBeIgnoredAgain() {
        ExceptionWorkItem ignored = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, 3, "金额", "金额为空", "")
                .ignore("原因", "finance", LocalDateTime.now());

        assertThrows(IllegalStateException.class,
                () -> ignored.ignore("再来一次", "finance", LocalDateTime.now()));
    }

    @Test
    void ignoreRequiresReasonOperatorAndTimestamp() {
        ExceptionWorkItem item = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, 3, "金额", "金额为空", "");
        LocalDateTime at = LocalDateTime.of(2026, 10, 22, 9, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> item.ignore(" ", "finance", at));
        assertThrows(IllegalArgumentException.class, () -> item.ignore("原因", " ", at));
        assertThrows(IllegalArgumentException.class, () -> item.ignore("原因", "finance", null));
    }

    @Test
    void reopenClearsIgnoreTrace() {
        ExceptionWorkItem ignored = ExceptionWorkItem.report("T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, 3, "金额", "金额为空", "")
                .ignore("原因", "finance", LocalDateTime.now());

        ExceptionWorkItem reopened = ignored.reopen();

        assertEquals(IssueStatus.OPEN, reopened.getStatus());
        assertNull(reopened.getIgnoredBy());
        assertNull(reopened.getIgnoredAt());
        assertNull(reopened.getIgnoreReason());
    }

    @Test
    void requiredKeysAndSeverityStatusAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> ExceptionWorkItem.report(" ", "OBJ-1", "CODE", IssueSeverity.WARNING,
                        1, null, "msg", null));
        assertThrows(IllegalArgumentException.class,
                () -> ExceptionWorkItem.report("T-1", "", "CODE", IssueSeverity.WARNING,
                        1, null, "msg", null));
        assertThrows(IllegalArgumentException.class,
                () -> ExceptionWorkItem.report("T-1", "OBJ-1", null, IssueSeverity.WARNING,
                        1, null, "msg", null));
        assertThrows(IllegalArgumentException.class,
                () -> ExceptionWorkItem.report("T-1", "OBJ-1", "CODE", null, 1, null, "msg", null));
        assertThrows(IllegalArgumentException.class,
                () -> ExceptionWorkItem.report("T-1", "OBJ-1", "CODE", IssueSeverity.WARNING,
                        -1, null, "msg", null));
    }

    @Test
    void restoreRoundTripsAllFields() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 22, 9, 0, 0);
        ExceptionWorkItem item = ExceptionWorkItem.restore(7L, "T-1", "OBJ-1", "FIELD_EMPTY",
                IssueSeverity.WARNING, IssueStatus.IGNORED, 3, "金额", "金额为空", "",
                "finance", at, "原因");

        assertEquals(7L, item.getId());
        assertEquals(IssueStatus.IGNORED, item.getStatus());
        assertEquals("finance", item.getIgnoredBy());
        assertEquals(at, item.getIgnoredAt());
        assertEquals("原因", item.getIgnoreReason());
    }
}
