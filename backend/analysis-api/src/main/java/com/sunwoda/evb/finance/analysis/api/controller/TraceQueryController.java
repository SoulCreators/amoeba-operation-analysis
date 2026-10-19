package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.TraceQueryServiceImpl;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** HTTP entry point for user-scoped calculation fact provenance queries. */
@RestController
@RequestMapping("/api/v1/calculations")
public class TraceQueryController {
    private final TraceQueryServiceImpl traceQueryService;

    public TraceQueryController(TraceQueryServiceImpl traceQueryService) {
        this.traceQueryService = traceQueryService;
    }

    @GetMapping("/{batchNo}/trace")
    public ApiResult<List<PnlFactSource>> trace(@PathVariable String batchNo,
                                                 @RequestParam String perspective,
                                                 @RequestParam String taskNo,
                                                 Principal principal) {
        return ApiResult.ok(traceQueryService.findForUser(
                batchNo, AnalysisPerspective.fromCode(perspective), taskNo, requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
