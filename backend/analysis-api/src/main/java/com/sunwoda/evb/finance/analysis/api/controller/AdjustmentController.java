package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.AdjustmentRequest;
import com.sunwoda.evb.finance.analysis.api.dto.VoidAdjustmentRequest;
import com.sunwoda.evb.finance.analysis.application.service.AdjustmentService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/adjustments")
public class AdjustmentController {
    private final AdjustmentService service;

    public AdjustmentController(AdjustmentService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<Adjustment> create(@RequestBody AdjustmentRequest request, Principal principal) {
        // 本次修改：录入以认证主体为可信操作者，客户端只提交调整内容，不提交授权凭据。
        return ApiResult.ok(service.create(
                request.getPeriod(), AnalysisPerspective.fromCode(request.getPerspective()),
                request.getScope(), AdjustmentScenario.fromCode(request.getScenario()),
                request.getMetricCode(), request.getAmount(),
                AdjustmentDirection.valueOf(request.getDirection()),
                request.getReason(), request.getEvidenceFileId(), request.getExpectedVersion(),
                requiredUser(principal)));
    }

    @GetMapping
    public ApiResult<List<Adjustment>> list(@RequestParam String period,
                                            @RequestParam String perspective,
                                            Principal principal) {
        return ApiResult.ok(service.list(period, AnalysisPerspective.fromCode(perspective),
                requiredUser(principal, "未获取到认证用户，拒绝返回调整单")));
    }

    @PostMapping("/{adjustmentId}/void")
    public ApiResult<Adjustment> voidAdjustment(@PathVariable String adjustmentId,
                                                @RequestBody VoidAdjustmentRequest request,
                                                Principal principal) {
        return ApiResult.ok(service.voidAdjustment(adjustmentId, request.getReason(),
                requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        return requiredUser(principal, "未获取到认证用户");
    }

    private String requiredUser(Principal principal, String message) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
        return principal.getName().trim();
    }
}
