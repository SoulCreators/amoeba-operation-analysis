package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.MetricTreeService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.MetricResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metric-tree")
public class MetricTreeController {
    private final MetricTreeService service;

    public MetricTreeController(MetricTreeService service) {
        this.service = service;
    }

    @GetMapping("/{batchNo}")
    public ApiResult<List<MetricResult>> calculate(@PathVariable String batchNo, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝返回指标树");
        }
        return ApiResult.ok(service.calculate(batchNo, principal.getName()));
    }
}
