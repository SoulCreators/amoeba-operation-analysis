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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/batches/{batchNo}/imports")
public class ImportTaskController {
    private final ImportTaskService importTaskService;

    public ImportTaskController(ImportTaskService importTaskService) {
        this.importTaskService = importTaskService;
    }

    @PostMapping
    public ApiResult<ImportTask> create(@PathVariable String batchNo,
                                        @RequestBody CreateImportTaskRequest request) {
        return ApiResult.ok(importTaskService.create(batchNo, request.getDatasetCode(),
                request.getFileName(), request.getChecksum(), request.getCreatedBy()));
    }

}
