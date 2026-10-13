package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateFcVersionRequest;
import com.sunwoda.evb.finance.analysis.application.service.FcVersionCommandService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;

/** FC 版本草稿、快照导入和正式发布接口。 */
@RestController
@RequestMapping("/api/v1/fc-versions")
public class FcVersionController {
    private final FcVersionCommandService service;

    public FcVersionController(FcVersionCommandService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<FcVersion> create(@RequestBody CreateFcVersionRequest request, Principal principal) {
        String operator = requireUser(principal);
        return ApiResult.ok(service.create(request.getVersionCode(), perspective(request.getPerspective()),
                request.getBasePeriod(), request.getCoverageStart(), request.getCoverageEnd(), operator));
    }

    @PostMapping(value = "/{versionCode}/snapshots", consumes = "multipart/form-data")
    public ApiResult<ImportValidationResult> upload(@PathVariable String versionCode,
                                                     @RequestParam String perspective,
                                                     @RequestParam String basePeriod,
                                                     @RequestPart("file") MultipartFile file,
                                                     Principal principal) {
        String operator = requireUser(principal);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("FC快照文件不能为空");
        try {
            return ApiResult.ok(service.uploadSnapshots(versionCode, perspective(perspective), basePeriod,
                    file.getInputStream(), operator));
        } catch (IOException ex) {
            throw new IllegalArgumentException("FC快照文件无法读取: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/{versionCode}/publish")
    public ApiResult<FcVersion> publish(@PathVariable String versionCode,
                                        @RequestParam String perspective,
                                        @RequestParam String basePeriod,
                                        Principal principal) {
        return ApiResult.ok(service.publish(versionCode, perspective(perspective), basePeriod,
                requireUser(principal)));
    }

    private AnalysisPerspective perspective(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("perspective不能为空");
        return AnalysisPerspective.fromCode(value.trim());
    }

    private String requireUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }
}
