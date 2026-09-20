package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.UpsertMetricRequest;
import com.sunwoda.evb.finance.analysis.application.service.MetricService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.MetricDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricController {
    private final MetricService service;

    public MetricController(MetricService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<MetricDefinition>> list(
            @RequestParam(required = false) String perspective,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResult.ok(service.list(perspective, includeDisabled));
    }

    @PostMapping
    public ApiResult<MetricDefinition> upsert(@RequestBody UpsertMetricRequest request, Principal principal) {
        return ApiResult.ok(service.upsert(request.getMetricCode(), request.getMetricName(),
                request.getApplicablePerspective(), request.getUnit(), request.getFormulaExpression(),
                request.getDisplayOrder(), request.isEnabled(), request.getAiSummaryTemplate(),
                requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
