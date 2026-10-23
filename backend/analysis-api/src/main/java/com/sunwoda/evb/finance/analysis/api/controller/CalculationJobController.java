package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.model.CalculationJobDetail;
import com.sunwoda.evb.finance.analysis.application.service.CalculationStageLogService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 计算作业详情查询接口。
 *
 * <p>只暴露阶段、处理/异常行数、心跳、错误码、可重试性与阶段日志；失败作业不返回
 * 任何部分正式金额。客户端传入的 jobId 只是查询键，不是授权凭据。</p>
 */
@RestController
@RequestMapping("/api/v1/calculations")
public class CalculationJobController {
    private final CalculationStageLogService service;

    public CalculationJobController(CalculationStageLogService service) {
        this.service = service;
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResult<CalculationJobDetail> getJob(@PathVariable String jobId, Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(service.getJobDetail(jobId));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝查看计算作业详情");
        }
        return principal.getName().trim();
    }
}
