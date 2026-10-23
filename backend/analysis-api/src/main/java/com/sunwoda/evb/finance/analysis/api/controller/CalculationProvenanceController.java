package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.model.CalculationProvenance;
import com.sunwoda.evb.finance.analysis.application.service.CalculationProvenanceService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 历史上游配置重放只读 HTTP 入口。
 *
 * <p>请求身份只从认证 Principal 获取，路径参数中的版本号/追踪号只是查询键，
 * 不参与授权。返回该结果冻结的配置内容与来源版本集合原文，供审计还原。</p>
 */
@RestController
@Profile("db")
@RequestMapping("/api/v1/calculation-provenance")
public class CalculationProvenanceController {
    private final CalculationProvenanceService service;

    public CalculationProvenanceController(CalculationProvenanceService service) {
        if (service == null) {
            throw new IllegalArgumentException("service must not be null");
        }
        this.service = service;
    }

    @GetMapping("/by-version/{versionId}")
    public ApiResult<CalculationProvenance> byVersion(@PathVariable String versionId, Principal principal) {
        return ApiResult.ok(service.replayByVersion(versionId, requiredUser(principal)));
    }

    @GetMapping("/by-trace/{traceId}")
    public ApiResult<CalculationProvenance> byTrace(@PathVariable String traceId, Principal principal) {
        return ApiResult.ok(service.replayByTrace(traceId, requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
