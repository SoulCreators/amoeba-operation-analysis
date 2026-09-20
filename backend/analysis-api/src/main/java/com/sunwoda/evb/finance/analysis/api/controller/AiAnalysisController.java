package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.AiAnalysisService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/ai/analysis")
public class AiAnalysisController {
    private final AiAnalysisService service;

    public AiAnalysisController(AiAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/{batchNo}")
    public ApiResult<AiAnalysisDraft> generate(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.generate(batchNo, requiredUser(principal)));
    }

    @GetMapping("/{batchNo}")
    public ApiResult<AiAnalysisDraft> get(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.get(batchNo, requiredUser(principal)));
    }

    @PostMapping("/{batchNo}/publish")
    public ApiResult<AiAnalysisDraft> publish(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.publish(batchNo, requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝访问AI分析");
        }
        return principal.getName();
    }
}
