package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ExceptionWorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 异常工作台应用服务契约：筛选、受控忽略、修复复检与阻断闭环。 */
class ExceptionWorkbenchServiceTest {
    private static final String TASK_NO = "T-1";

    private FakeWorkItemRepository repository;
    private ExceptionWorkbenchService service;

    @BeforeEach
    void setUp() {
        repository = new FakeWorkItemRepository();
        service = new ExceptionWorkbenchServiceImpl(repository);
    }

    @Test
    void listFiltersBySeverityErrorCodeObjectAndStatus() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "FIELD_EMPTY", IssueSeverity.BLOCKING, 1),
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 2),
                report("OBJ-2", "FIELD_EMPTY", IssueSeverity.WARNING, 3)));

        assertEquals(3, service.list(TASK_NO, null, null, null, null).size());
        assertEquals(1, service.list(TASK_NO, IssueSeverity.BLOCKING, null, null, null).size());
        assertEquals(2, service.list(TASK_NO, null, "FIELD_EMPTY", null, null).size());
        assertEquals(1, service.list(TASK_NO, null, null, "OBJ-2", null).size());
        assertEquals(3, service.list(TASK_NO, null, null, null, IssueStatus.OPEN).size());
        assertEquals(0, service.list(TASK_NO, null, null, null, IssueStatus.IGNORED).size());
    }

    @Test
    void ignoreWarningRecordsAuditAndPersists() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 1)));
        Long id = repository.findByTaskNo(TASK_NO).get(0).getId();

        ExceptionWorkItem ignored = service.ignore(TASK_NO, id, "历史空值", "finance");

        assertEquals(IssueStatus.IGNORED, ignored.getStatus());
        assertEquals("finance", ignored.getIgnoredBy());
        assertEquals("历史空值", ignored.getIgnoreReason());
        assertTrue(repository.findByTaskNo(TASK_NO).get(0).isIgnored());
    }

    @Test
    void ignoreBlockingIsRejected() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "FIELD_EMPTY", IssueSeverity.BLOCKING, 1)));
        Long id = repository.findByTaskNo(TASK_NO).get(0).getId();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.ignore(TASK_NO, id, "原因", "finance"));
        assertEquals("阻断项不可忽略: FIELD_EMPTY", ex.getMessage());
    }

    @Test
    void ignoreUnknownOrMismatchedTaskIsRejected() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 1)));
        Long id = repository.findByTaskNo(TASK_NO).get(0).getId();

        assertThrows(IllegalArgumentException.class,
                () -> service.ignore(TASK_NO, 999L, "原因", "finance"));
        assertThrows(IllegalArgumentException.class,
                () -> service.ignore("OTHER-TASK", id, "原因", "finance"));
    }

    @Test
    void recheckReopensIgnoredWarnings() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "FIELD_EMPTY", IssueSeverity.BLOCKING, 1),
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 2)));
        Long warningId = repository.findByTaskNo(TASK_NO).get(1).getId();
        service.ignore(TASK_NO, warningId, "历史空值", "finance");

        List<ExceptionWorkItem> reopened = service.recheck(TASK_NO, "finance");

        assertEquals(2, reopened.size());
        assertTrue(reopened.stream().allMatch(ExceptionWorkItem::isOpen));
        assertEquals(0, service.list(TASK_NO, null, null, null, IssueStatus.IGNORED).size());
    }

    @Test
    void hasBlockingOpenReflectsClosureGate() {
        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 1)));
        assertFalse(service.hasBlockingOpen(TASK_NO));

        repository.replaceByTaskNo(TASK_NO, Arrays.asList(
                report("OBJ-1", "FIELD_EMPTY", IssueSeverity.BLOCKING, 1),
                report("OBJ-1", "TYPE_INVALID", IssueSeverity.WARNING, 2)));
        assertTrue(service.hasBlockingOpen(TASK_NO));
    }

    @Test
    void blankTaskNoIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.list(" ", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.ignore(" ", 1L, "原因", "finance"));
        assertThrows(IllegalArgumentException.class,
                () -> service.recheck(" ", "finance"));
        assertThrows(IllegalArgumentException.class,
                () -> service.hasBlockingOpen(" "));
    }

    private static ExceptionWorkItem report(String objectCode, String issueCode,
                                            IssueSeverity severity, int rowNo) {
        return ExceptionWorkItem.report(TASK_NO, objectCode, issueCode, severity,
                rowNo, "金额", issueCode + " 说明", "");
    }

    /** 应用层测试自带的异常工作项仓储。 */
    private static final class FakeWorkItemRepository implements ExceptionWorkItemRepository {
        private final Map<String, List<ExceptionWorkItem>> byTask =
                new LinkedHashMap<String, List<ExceptionWorkItem>>();
        private final Map<Long, ExceptionWorkItem> byId =
                new LinkedHashMap<Long, ExceptionWorkItem>();
        private long nextId = 1;

        @Override
        public List<ExceptionWorkItem> replaceByTaskNo(String taskNo, List<ExceptionWorkItem> items) {
            List<ExceptionWorkItem> existing = byTask.get(taskNo);
            if (existing != null) {
                for (ExceptionWorkItem item : existing) {
                    byId.remove(item.getId());
                }
            }
            List<ExceptionWorkItem> assigned = new ArrayList<ExceptionWorkItem>();
            if (items != null) {
                for (ExceptionWorkItem item : items) {
                    ExceptionWorkItem withId = item.withId(nextId++);
                    assigned.add(withId);
                    byId.put(withId.getId(), withId);
                }
            }
            byTask.put(taskNo, assigned);
            return assigned;
        }

        @Override
        public List<ExceptionWorkItem> findByTaskNo(String taskNo) {
            List<ExceptionWorkItem> found = byTask.get(taskNo);
            return found == null ? Collections.<ExceptionWorkItem>emptyList()
                    : new ArrayList<ExceptionWorkItem>(found);
        }

        @Override
        public Optional<ExceptionWorkItem> findById(Long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public ExceptionWorkItem update(ExceptionWorkItem item) {
            byId.put(item.getId(), item);
            for (Map.Entry<String, List<ExceptionWorkItem>> entry : byTask.entrySet()) {
                List<ExceptionWorkItem> list = entry.getValue();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId().equals(item.getId())) {
                        list.set(i, item);
                    }
                }
            }
            return item;
        }
    }
}
