package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.BatchReadinessService;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
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
    private final BatchService batchService;
    private final PermissionService permissionService;

    public BatchReadinessController(BatchReadinessService service, BatchService batchService,
                                    PermissionService permissionService) {
        this.service = service;
        this.batchService = batchService;
        this.permissionService = permissionService;
    }

    @GetMapping("/{batchNo}/readiness")
    public ApiResult<BatchReadiness> get(@PathVariable String batchNo, Principal principal) {
        String userId = requiredUser(principal);
        AnalysisBatch batch = batchService.get(batchNo);
        if (!permissionService.canViewScope(userId, batch.getPerspective(), batch.getScopeCode())) {
            throw new IllegalStateException("当前用户无批次所属范围的查看权限");
        }
        return ApiResult.ok(service.get(batchNo));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝查看批次完整性");
        }
        return principal.getName().trim();
    }
}
