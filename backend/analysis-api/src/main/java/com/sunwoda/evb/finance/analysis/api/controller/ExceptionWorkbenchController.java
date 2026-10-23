package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.IgnoreIssueRequest;
import com.sunwoda.evb.finance.analysis.application.service.ExceptionWorkbenchService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** 异常工作台：按级别/错误码/对象/处理状态筛选，受控忽略与修复复检。 */
@RestController
@RequestMapping("/api/v1/imports")
public class ExceptionWorkbenchController {
    private final ExceptionWorkbenchService service;

    public ExceptionWorkbenchController(ExceptionWorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/{taskNo}/workbench")
    public ApiResult<List<ExceptionWorkItem>> list(@PathVariable String taskNo,
                                                   @RequestParam(required = false) String severity,
                                                   @RequestParam(required = false) String issueCode,
                                                   @RequestParam(required = false) String objectCode,
                                                   @RequestParam(required = false) String status,
                                                   Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(service.list(taskNo, parseSeverity(severity), issueCode,
                objectCode, parseStatus(status)));
    }

    @PostMapping("/{taskNo}/workbench/{issueId}/ignore")
    public ApiResult<ExceptionWorkItem> ignore(@PathVariable String taskNo,
                                               @PathVariable Long issueId,
                                               @RequestBody IgnoreIssueRequest request,
                                               Principal principal) {
        String operator = requiredUser(principal);
        String reason = request == null ? null : request.getReason();
        return ApiResult.ok(service.ignore(taskNo, issueId, reason, operator));
    }

    @PostMapping("/{taskNo}/workbench/recheck")
    public ApiResult<List<ExceptionWorkItem>> recheck(@PathVariable String taskNo,
                                                      Principal principal) {
        String operator = requiredUser(principal);
        return ApiResult.ok(service.recheck(taskNo, operator));
    }

    private IssueSeverity parseSeverity(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return IssueSeverity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("级别无效: " + value);
        }
    }

    private IssueStatus parseStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return IssueStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("处理状态无效: " + value);
        }
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
