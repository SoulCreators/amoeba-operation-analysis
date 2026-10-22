package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.DataQualityService;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.DataQualityReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/batches")
public class DataQualityController {
    private final DataQualityService service;
    private final BatchService batchService;
    private final PermissionService permissionService;

    public DataQualityController(DataQualityService service, BatchService batchService,
                                 PermissionService permissionService) {
        this.service = service;
        this.batchService = batchService;
        this.permissionService = permissionService;
    }

    @GetMapping("/{batchNo}/quality")
    public ApiResult<DataQualityReport> get(@PathVariable String batchNo, Principal principal) {
        String userId = requiredUser(principal);
        AnalysisBatch batch = batchService.get(batchNo);
        if (permissionService.viewableScopes(userId, batch.getPerspective()).isEmpty()) {
            throw new IllegalStateException("当前用户无批次所属视角的查看权限");
        }
        return ApiResult.ok(service.get(batchNo));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝查看批次数据质量");
        }
        return principal.getName().trim();
    }
}
