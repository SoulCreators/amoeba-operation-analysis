package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateBatchRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveBatchRequest;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResult<AnalysisBatch> create(@RequestBody CreateBatchRequest request) {
        return ApiResult.ok(batchService.create(request.getBatchNo(), request.getPeriod(),
                AnalysisPerspective.fromCode(request.getPerspective()), request.getCreatedBy()));
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
}
