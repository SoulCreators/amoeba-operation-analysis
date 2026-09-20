package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.BatchReadiness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchReadinessController {
    private final BatchReadinessService service;

    public BatchReadinessController(BatchReadinessService service) { this.service = service; }

    @GetMapping("/{batchNo}/readiness")
    public ApiResult<BatchReadiness> get(@PathVariable String batchNo, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝查看批次完整性");
        }
        return ApiResult.ok(service.get(batchNo));
    }
}
