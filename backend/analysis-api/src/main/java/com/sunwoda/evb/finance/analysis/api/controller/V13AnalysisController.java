package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.V13AnalysisService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/** 经营分析平台 V1.3 查询接口。 */
@RestController
@RequestMapping("/api/v1/v13")
public class V13AnalysisController {
    private final V13AnalysisService service;

    public V13AnalysisController(V13AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/overview/{batchNo}")
    public ApiResult<Map<String, Object>> overview(@PathVariable String batchNo,
                                                    @RequestParam(required = false, defaultValue = "ACT_BGT") String scenario,
                                                    Principal principal) {
        return ApiResult.ok(service.overview(batchNo, requireUser(principal), scenario));
    }

    @GetMapping("/matrix/{batchNo}")
    public ApiResult<Map<String, Object>> matrix(@PathVariable String batchNo,
                                                  @RequestParam(required = false, defaultValue = "GROSS_PROFIT") String metric,
                                                  Principal principal) {
        return ApiResult.ok(service.matrix(batchNo, requireUser(principal), metric));
    }

    @GetMapping("/unit-efficiency/{batchNo}")
    public ApiResult<Map<String, Object>> unitEfficiency(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.unitEfficiency(batchNo, requireUser(principal)));
    }

    @GetMapping("/forecast/{batchNo}")
    public ApiResult<Map<String, Object>> forecast(@PathVariable String batchNo,
                                                    @RequestParam(required = false, defaultValue = "ACT_BGT") String scenario,
                                                    @RequestParam(required = false) String fcVersion,
                                                    Principal principal) {
        return ApiResult.ok(service.forecast(batchNo, requireUser(principal), scenario, fcVersion));
    }

    @GetMapping("/risk/{batchNo}")
    public ApiResult<Map<String, Object>> risk(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.risk(batchNo, requireUser(principal)));
    }

    @GetMapping("/waterfall/{batchNo}")
    public ApiResult<Map<String, Object>> waterfall(@PathVariable String batchNo, Principal principal) {
        return ApiResult.ok(service.waterfall(batchNo, requireUser(principal)));
    }

    @GetMapping("/topics/{batchNo}/{topicCode}")
    public ApiResult<Map<String, Object>> topic(@PathVariable String batchNo,
                                                 @PathVariable String topicCode,
                                                 Principal principal) {
        return ApiResult.ok(service.topic(batchNo, requireUser(principal), topicCode));
    }

    private String requireUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝返回 V1.3 分析结果");
        }
        return principal.getName().trim();
    }
}
