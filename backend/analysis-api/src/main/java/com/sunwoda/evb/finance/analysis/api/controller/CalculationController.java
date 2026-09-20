package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CalculateRequest;
import com.sunwoda.evb.finance.analysis.api.dto.ResultLifecycleRequest;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calculations")
public class CalculationController {
    private final CalculationService service;

    public CalculationController(CalculationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<CalculationResult> calculate(@RequestBody CalculateRequest request) {
        return ApiResult.ok(service.calculate(request.getBatchNo()));
    }

    @GetMapping("/{batchNo}")
    public ApiResult<CalculationResult> get(@PathVariable String batchNo) {
        return ApiResult.ok(service.get(batchNo));
    }

    @PostMapping("/confirm")
    public ApiResult<CalculationResult> confirm(@RequestBody ResultLifecycleRequest request) {
        return ApiResult.ok(service.confirm(request.getBatchNo(), request.getOperator()));
    }

    @PostMapping("/publish")
    public ApiResult<CalculationResult> publish(@RequestBody ResultLifecycleRequest request) {
        return ApiResult.ok(service.publish(request.getBatchNo(), request.getOperator()));
    }
}
