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
import java.security.Principal;

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
    public ApiResult<CalculationResult> get(@PathVariable String batchNo,
                                            Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝返回经营结果");
        }
        return ApiResult.ok(service.getForUser(batchNo, principal.getName()));
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
