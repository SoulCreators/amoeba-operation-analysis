package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateBatchRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveBatchRequest;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    private final BatchService batchService;
    private final PermissionService permissionService;

    public BatchController(BatchService batchService, PermissionService permissionService) {
        this.batchService = batchService;
        this.permissionService = permissionService;
    }

    @PostMapping
    public ApiResult<AnalysisBatch> create(@RequestBody CreateBatchRequest request, Principal principal) {
        return ApiResult.ok(batchService.create(request.getBatchNo(), request.getPeriod(),
                AnalysisPerspective.fromCode(request.getPerspective()), requiredUser(principal)));
    }

    @GetMapping("/{batchNo}")
    public ApiResult<AnalysisBatch> get(@PathVariable String batchNo, Principal principal) {
        String userId = requiredUser(principal);
        AnalysisBatch batch = batchService.get(batchNo);
        if (permissionService.viewableScopes(userId, batch.getPerspective()).isEmpty()) {
            throw new IllegalStateException("当前用户无批次所属视角的查看权限");
        }
        return ApiResult.ok(batch);
    }

    @PostMapping("/{batchNo}/status")
    public ApiResult<AnalysisBatch> move(@PathVariable String batchNo,
                                         @RequestBody MoveBatchRequest request,
                                         Principal principal) {
        requiredUser(principal);
        // 状态流转必须经过计算、确认或发布等专用应用服务，不能由通用 HTTP 状态接口绕过动作授权。
        throw new IllegalStateException("批次状态只能由具备对应权限的专用业务操作推动");
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
