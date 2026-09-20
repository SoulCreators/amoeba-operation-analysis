package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import com.sunwoda.evb.finance.analysis.domain.repository.ImportIssueRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryImportIssueRepository implements ImportIssueRepository {
    private final ConcurrentMap<String, List<ImportIssue>> store =
            new ConcurrentHashMap<String, List<ImportIssue>>();

    @Override
    public void replace(String taskNo, List<ImportIssue> issues) {
        store.put(taskNo, Collections.unmodifiableList(new ArrayList<ImportIssue>(issues)));
    }

    @Override
    public List<ImportIssue> findByTaskNo(String taskNo) {
        List<ImportIssue> issues = store.get(taskNo);
        return issues == null ? Collections.<ImportIssue>emptyList() : issues;
    }
}
