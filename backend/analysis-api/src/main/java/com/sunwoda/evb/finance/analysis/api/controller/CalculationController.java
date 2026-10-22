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
    public ApiResult<CalculationResult> calculate(@RequestBody CalculateRequest request,
                                                  Principal principal) {
        String userId = requiredUser(principal);
        // 本次修改：计算命令沿用认证主体作为可信操作者，避免无操作者入口绕过总部财经权限校验。
        service.calculate(request.getBatchNo(), userId);
        return ApiResult.ok(service.getForUser(request.getBatchNo(), userId));
    }

    @GetMapping("/{batchNo}")
    public ApiResult<CalculationResult> get(@PathVariable String batchNo,
                                            Principal principal) {
        return ApiResult.ok(service.getForUser(batchNo,
                requiredUser(principal, "未获取到认证用户，拒绝返回经营结果")));
    }

    @GetMapping("/{batchNo}/versions/{versionNo}/result")
    public ApiResult<CalculationResult> versionResult(@PathVariable String batchNo, @PathVariable String versionNo,
                                                     Principal principal) {
        return ApiResult.ok(service.getVersionForUser(batchNo, versionNo, requiredUser(principal)));
    }

    @PostMapping("/confirm")
    public ApiResult<CalculationResult> confirm(@RequestBody ResultLifecycleRequest request, Principal principal) {
        return ApiResult.ok(service.confirm(request.getBatchNo(), requiredUser(principal)));
    }

    @PostMapping("/publish")
    public ApiResult<CalculationResult> publish(@RequestBody ResultLifecycleRequest request, Principal principal) {
        return ApiResult.ok(service.publish(request.getBatchNo(), requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        return requiredUser(principal, "未获取到认证用户");
    }

    private String requiredUser(Principal principal, String message) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException(message);
        }
        // Principal 是认证边界的唯一用户来源；这里只规范化首尾空白，不改变身份值、授权范围或业务状态。
        return principal.getName().trim();
    }
}
