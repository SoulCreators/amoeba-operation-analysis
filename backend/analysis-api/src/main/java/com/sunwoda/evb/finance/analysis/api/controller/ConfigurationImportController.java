package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import com.sunwoda.evb.finance.analysis.domain.model.ImportIssue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1/config/imports")
public class ConfigurationImportController {
    private final ConfigurationImportService service;
    private final PermissionService permissionService;

    public ConfigurationImportController(ConfigurationImportService service) {
        this(service, null);
    }

    /** 本次修改：生产接线对用户权限模板执行 ADMIN 强校验，单参数构造器保留既有切片测试。 */
    @Autowired
    public ConfigurationImportController(ConfigurationImportService service,
                                         PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @PostMapping
    public ApiResult<ConfigurationImportTask> create(@RequestBody CreateConfigurationImportRequest request,
                                                     Principal principal) {
        ConfigurationImportType type = ConfigurationImportType.valueOf(request.getType());
        String operator = requiredUser(principal);
        requirePermissionAdmin(type, operator);
        return ApiResult.ok(service.create(type, request.getFileName(), request.getChecksum(), operator));
    }

    @GetMapping("/{taskNo}")
    public ApiResult<ConfigurationImportTask> get(@PathVariable String taskNo, Principal principal) {
        String operator = requiredUser(principal);
        ConfigurationImportTask task = service.get(taskNo);
        requirePermissionAdmin(task.getType(), operator);
        return ApiResult.ok(task);
    }

    @GetMapping("/{taskNo}/issues")
    public ApiResult<List<ImportIssue>> issues(@PathVariable String taskNo, Principal principal) {
        requirePermissionAdmin(taskNo, requiredUser(principal));
        return ApiResult.ok(service.issues(taskNo));
    }

    @PostMapping(value = "/{taskNo}/upload", consumes = "multipart/form-data")
    public ApiResult<ConfigurationImportTask> upload(@PathVariable String taskNo,
                                                     @RequestPart("file") MultipartFile file,
                                                     Principal principal) {
        requirePermissionAdmin(taskNo, requiredUser(principal));
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
        requirePermissionAdmin(taskNo, requiredUser(principal));
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("配置文件不能为空");
        try {
            return ApiResult.ok(service.validate(taskNo, file.getInputStream()));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("配置文件无法读取: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/{taskNo}/status")
    public ApiResult<ConfigurationImportTask> move(@PathVariable String taskNo,
                                                  @RequestBody MoveConfigurationImportRequest request,
                                                  Principal principal) {
        requirePermissionAdmin(taskNo, requiredUser(principal));
        return ApiResult.ok(service.move(taskNo, ImportStatus.valueOf(request.getStatus())));
    }

    private void requirePermissionAdmin(ConfigurationImportType type, String operator) {
        if (permissionService != null && type == ConfigurationImportType.USER_PERMISSION
                && !permissionService.isEnabledAdmin(operator)) {
            throw new IllegalStateException("仅ADMIN可导入用户权限模板");
        }
    }

    private void requirePermissionAdmin(String taskNo, String operator) {
        if (permissionService != null) requirePermissionAdmin(service.get(taskNo).getType(), operator);
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
