package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateExportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ExportDownloadResponse;
import com.sunwoda.evb.finance.analysis.api.dto.ExportTaskResponse;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ExportService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping
    public ApiResult<ExportTaskResponse> create(@RequestBody CreateExportRequest request,
                                                Principal principal) {
        ExportJob job = exportService.create(requiredUser(principal), toApplicationRequest(request));
        return ApiResult.ok(ExportTaskResponse.from(job));
    }

    @GetMapping("/{taskId}")
    public ApiResult<ExportTaskResponse> get(@PathVariable String taskId, Principal principal) {
        Optional<ExportJob> job = exportService.status(requiredUser(principal), required(taskId, "taskId不能为空"));
        if (!job.isPresent()) {
            throw new IllegalStateException("导出任务不存在、未授权或已不可用");
        }
        return ApiResult.ok(ExportTaskResponse.from(job.get()));
    }

    @GetMapping("/{taskId}/download")
    public ApiResult<ExportDownloadResponse> download(@PathVariable String taskId,
                                                      Principal principal) {
        return exportService.download(requiredUser(principal), required(taskId, "taskId不能为空"))
                .map(file -> ApiResult.ok(new ExportDownloadResponse(file)))
                .orElseThrow(() -> new IllegalStateException(
                        "导出文件不存在、未授权、未完成或已过期"));
    }

    private ExportRequest toApplicationRequest(CreateExportRequest request) {
        if (request == null) throw new IllegalArgumentException("导出请求不能为空");
        return ExportRequest.fromFormatCode(
                required(request.getRequestKey(), "requestKey不能为空"),
                required(request.getBatchNo(), "batchNo不能为空"),
                required(request.getResultVersion(), "resultVersion不能为空"),
                AnalysisPerspective.fromCode(required(request.getPerspective(), "perspective不能为空")),
                required(request.getFormat(), "format不能为空"),
                parseResultStatus(request.getResultStatus()),
                requestedScopes(request.getRequestedScopes()));
    }

    private CalculationResultStatus parseResultStatus(String value) {
        String normalized = required(value, "resultStatus不能为空").toUpperCase(java.util.Locale.ROOT);
        try {
            return CalculationResultStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("不支持的结果状态: " + value);
        }
    }

    private Set<String> requestedScopes(java.util.List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) return Collections.emptySet();
        Set<String> normalized = new LinkedHashSet<String>();
        for (String scope : scopes) {
            if (scope != null && !scope.trim().isEmpty()) normalized.add(scope.trim());
        }
        return normalized;
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
