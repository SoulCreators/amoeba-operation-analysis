package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/config/imports")
public class ConfigurationImportController {
    private final ConfigurationImportService service;

    public ConfigurationImportController(ConfigurationImportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<ConfigurationImportTask> create(@RequestBody CreateConfigurationImportRequest request,
                                                     Principal principal) {
        return ApiResult.ok(service.create(ConfigurationImportType.valueOf(request.getType()),
                request.getFileName(), request.getChecksum(), requiredUser(principal)));
    }

    @GetMapping("/{taskNo}")
    public ApiResult<ConfigurationImportTask> get(@PathVariable String taskNo) {
        return ApiResult.ok(service.get(taskNo));
    }

    @PostMapping(value = "/{taskNo}/upload", consumes = "multipart/form-data")
    public ApiResult<ConfigurationImportTask> upload(@PathVariable String taskNo,
                                                     @RequestPart("file") MultipartFile file,
                                                     Principal principal) {
        requiredUser(principal);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("配置文件不能为空");
        try {
            return ApiResult.ok(service.upload(taskNo, file.getInputStream()));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("配置文件无法读取: " + ex.getMessage(), ex);
        }
    }

    @PostMapping(value = "/{taskNo}/validate", consumes = "multipart/form-data")
    public ApiResult<ImportValidationResult> validate(@PathVariable String taskNo,
                                                      @RequestPart("file") MultipartFile file,
                                                      Principal principal) {
        requiredUser(principal);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("配置文件不能为空");
        try {
            return ApiResult.ok(service.validate(taskNo, file.getInputStream()));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("配置文件无法读取: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/{taskNo}/status")
    public ApiResult<ConfigurationImportTask> move(@PathVariable String taskNo,
                                                  @RequestBody MoveConfigurationImportRequest request) {
        return ApiResult.ok(service.move(taskNo, ImportStatus.valueOf(request.getStatus())));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
