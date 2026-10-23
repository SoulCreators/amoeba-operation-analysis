package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.RegisterSourceFileVersionRequest;
import com.sunwoda.evb.finance.analysis.application.service.SourceFileVersionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 源文件版本治理接口：登记新版本、切换生效指针、查询版本历史。
 */
@RestController
@RequestMapping("/api/v1")
public class SourceFileVersionController {
    private final SourceFileVersionService sourceFileVersionService;

    public SourceFileVersionController(SourceFileVersionService sourceFileVersionService) {
        this.sourceFileVersionService = sourceFileVersionService;
    }

    @PostMapping("/batches/{batchNo}/file-versions")
    public ApiResult<SourceFileVersion> register(@PathVariable String batchNo,
                                                 @RequestBody RegisterSourceFileVersionRequest request,
                                                 Principal principal) {
        String uploadedBy = requiredUser(principal);
        return ApiResult.ok(sourceFileVersionService.register(batchNo, request.getDatasetCode(),
                request.getObjectCode(), request.getTemplateCode(), request.getTemplateVersion(),
                request.getFileHash(), request.getObjectKey(), request.getRowCount(),
                request.getReplaceReason(), uploadedBy));
    }

    @PostMapping("/file-versions/{fileId}/activate")
    public ApiResult<SourceFileVersion> activate(@PathVariable String fileId, Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(sourceFileVersionService.activate(fileId));
    }

    @GetMapping("/file-versions")
    public ApiResult<List<SourceFileVersion>> list(@RequestParam String period,
                                                   @RequestParam String objectCode,
                                                   Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(sourceFileVersionService.listVersions(period, objectCode));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
