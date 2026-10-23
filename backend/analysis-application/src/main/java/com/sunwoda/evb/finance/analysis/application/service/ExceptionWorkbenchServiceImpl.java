package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.ExceptionWorkItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 异常工作台应用服务实现。 */
@Service
public class ExceptionWorkbenchServiceImpl implements ExceptionWorkbenchService {
    private final ExceptionWorkItemRepository repository;

    public ExceptionWorkbenchServiceImpl(ExceptionWorkItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ExceptionWorkItem> list(String taskNo, IssueSeverity severity, String issueCode,
                                        String objectCode, IssueStatus status) {
        requireText(taskNo, "taskNo");
        List<ExceptionWorkItem> filtered = new ArrayList<ExceptionWorkItem>();
        for (ExceptionWorkItem item : repository.findByTaskNo(taskNo)) {
            if (severity != null && item.getSeverity() != severity) {
                continue;
            }
            if (!isBlank(issueCode) && !issueCode.equals(item.getIssueCode())) {
                continue;
            }
            if (!isBlank(objectCode) && !objectCode.equals(item.getObjectCode())) {
                continue;
            }
            if (status != null && item.getStatus() != status) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    @Override
    public ExceptionWorkItem ignore(String taskNo, Long issueId, String reason, String operator) {
        requireText(taskNo, "taskNo");
        requireText(operator, "operator");
        if (issueId == null) {
            throw new IllegalArgumentException("issueId 不能为空");
        }
        ExceptionWorkItem item = repository.findById(issueId)
                .orElseThrow(() -> new IllegalArgumentException("工作项不存在: " + issueId));
        if (!taskNo.equals(item.getTaskNo())) {
            throw new IllegalArgumentException("工作项不属于该任务: " + issueId);
        }
        return repository.update(item.ignore(reason, operator, LocalDateTime.now()));
    }

    @Override
    public List<ExceptionWorkItem> recheck(String taskNo, String operator) {
        requireText(taskNo, "taskNo");
        requireText(operator, "operator");
        List<ExceptionWorkItem> reopened = new ArrayList<ExceptionWorkItem>();
        for (ExceptionWorkItem item : repository.findByTaskNo(taskNo)) {
            reopened.add(item.isIgnored() ? item.reopen() : item);
        }
        repository.replaceByTaskNo(taskNo, reopened);
        return reopened;
    }

    @Override
    public boolean hasBlockingOpen(String taskNo) {
        requireText(taskNo, "taskNo");
        for (ExceptionWorkItem item : repository.findByTaskNo(taskNo)) {
            if (item.isBlocking() && item.isOpen()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
