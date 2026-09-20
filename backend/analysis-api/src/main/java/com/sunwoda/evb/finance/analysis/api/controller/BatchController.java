package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateBatchRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveBatchRequest;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResult<AnalysisBatch> create(@RequestBody CreateBatchRequest request, Principal principal) {
        return ApiResult.ok(batchService.create(request.getBatchNo(), request.getPeriod(),
                AnalysisPerspective.fromCode(request.getPerspective()), requiredUser(principal)));
    }

    @GetMapping("/{batchNo}")
    public ApiResult<AnalysisBatch> get(@PathVariable String batchNo) {
        return ApiResult.ok(batchService.get(batchNo));
    }

    @PostMapping("/{batchNo}/status")
    public ApiResult<AnalysisBatch> move(@PathVariable String batchNo,
                                         @RequestBody MoveBatchRequest request) {
        return ApiResult.ok(batchService.move(batchNo, BatchStatus.valueOf(request.getStatus())));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
