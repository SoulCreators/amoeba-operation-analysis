package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.UpsertMappingRequest;
import com.sunwoda.evb.finance.analysis.application.service.MappingService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.MappingEntry;
import com.sunwoda.evb.finance.analysis.domain.model.MappingType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/mappings")
public class MappingController {
    private final MappingService service;

    public MappingController(MappingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<MappingEntry>> list(@RequestParam String type,
                                              @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResult.ok(service.list(MappingType.valueOf(type), includeDisabled));
    }

    @PostMapping
    public ApiResult<MappingEntry> upsert(@RequestBody UpsertMappingRequest request, Principal principal) {
        return ApiResult.ok(service.upsert(MappingType.valueOf(request.getType()), request.getMatchKey(),
                request.getMappedValue(), request.isEnabled(), requiredUser(principal)));
    }

    @PostMapping("/disable")
    public ApiResult<MappingEntry> disable(@RequestBody UpsertMappingRequest request, Principal principal) {
        return ApiResult.ok(service.disable(MappingType.valueOf(request.getType()),
                request.getMatchKey(), requiredUser(principal)));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
