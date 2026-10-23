package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateImportTaskRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveImportTaskRequest;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/batches/{batchNo}/imports")
public class ImportTaskController {
    private final ImportTaskService importTaskService;

    public ImportTaskController(ImportTaskService importTaskService) {
        this.importTaskService = importTaskService;
    }

    @PostMapping
    public ApiResult<ImportTask> create(@PathVariable String batchNo,
                                        @RequestBody CreateImportTaskRequest request, Principal principal) {
        return ApiResult.ok(importTaskService.create(batchNo, request.getDatasetCode(),
                request.getFileName(), request.getChecksum(), requiredUser(principal)));
    }

    @PostMapping(value = "/workbook", consumes = "multipart/form-data")
    public ApiResult<List<ImportTask>> importWorkbook(@PathVariable String batchNo,
                                                      @RequestPart("file") MultipartFile file,
                                                      Principal principal) {
        String user = requiredUser(principal);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        try {
            return ApiResult.ok(importTaskService.importWorkbook(batchNo,
                    file.getOriginalFilename(), file.getInputStream(), user));
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("导入文件无法读取: " + ex.getMessage(), ex);
        }
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }

}
