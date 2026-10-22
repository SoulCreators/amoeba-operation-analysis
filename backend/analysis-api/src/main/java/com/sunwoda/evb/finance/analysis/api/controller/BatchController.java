package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateBatchRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveBatchRequest;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
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
        String userId = requiredUser(principal);
        AnalysisPerspective perspective = AnalysisPerspective.fromCode(request.getPerspective());
        if (!permissionService.canCalculate(userId, perspective)) {
            throw new IllegalStateException("当前用户无权限创建批次");
        }
        return ApiResult.ok(batchService.create(request.getBatchNo(), request.getPeriod(), perspective, userId));
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
        String userId = requiredUser(principal);
        BatchStatus nextStatus = BatchStatus.valueOf(request.getStatus());
        if (nextStatus != BatchStatus.CHECKING
                && nextStatus != BatchStatus.READY_FOR_CALCULATION) {
            throw new IllegalStateException("当前用户无权限通过通用状态接口推进该批次");
        }
        AnalysisBatch batch = batchService.get(batchNo);
        if (!permissionService.canCalculate(userId, batch.getPerspective())) {
            throw new IllegalStateException("当前用户无权限推进批次准备状态");
        }
        return ApiResult.ok(batchService.move(batchNo, nextStatus));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
