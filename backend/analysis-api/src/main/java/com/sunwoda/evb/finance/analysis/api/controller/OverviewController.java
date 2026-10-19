package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.OverviewService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.OverviewMetricResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {
    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public ApiResult<List<OverviewMetricResult>> query(
            @RequestParam String period,
            @RequestParam String perspective,
            @RequestParam(required = false, defaultValue = "ALL") String scope,
            Principal principal) {
        requireUser(principal);
        return ApiResult.ok(overviewService.query(period, AnalysisPerspective.fromCode(perspective), scope));
    }

    private void requireUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
    }
}
