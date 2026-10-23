package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.repository.ExceptionWorkItemRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 异常工作台工作项内存实现：按任务整期替换，主键顺序分配。 */
@Repository
public class InMemoryExceptionWorkItemRepository implements ExceptionWorkItemRepository {
    private final Map<String, List<ExceptionWorkItem>> byTask =
            new LinkedHashMap<String, List<ExceptionWorkItem>>();
    private final Map<Long, ExceptionWorkItem> byId =
            new LinkedHashMap<Long, ExceptionWorkItem>();
    private long nextId = 1;

    @Override
    public synchronized List<ExceptionWorkItem> replaceByTaskNo(String taskNo, List<ExceptionWorkItem> items) {
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
    public synchronized List<ExceptionWorkItem> findByTaskNo(String taskNo) {
        List<ExceptionWorkItem> found = byTask.get(taskNo);
        return found == null ? Collections.<ExceptionWorkItem>emptyList()
                : new ArrayList<ExceptionWorkItem>(found);
    }

    @Override
    public synchronized Optional<ExceptionWorkItem> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized ExceptionWorkItem update(ExceptionWorkItem item) {
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException("update 需要已分配主键的工作项");
        }
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
