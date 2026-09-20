package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.PvmService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.PvmResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pvm")
public class PvmController {
    private final PvmService service;

    public PvmController(PvmService service) {
        this.service = service;
    }

    @GetMapping("/{batchNo}")
    public ApiResult<List<PvmResult>> analyze(@PathVariable String batchNo, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户，拒绝返回PVM结果");
        }
        return ApiResult.ok(service.analyze(batchNo, principal.getName()));
    }
}
