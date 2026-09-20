package com.sunwoda.evb.finance.analysis.api.controller;

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
@RequestMapping("/api/v1/imports")
public class ImportTaskQueryController {
    private final ImportTaskService importTaskService;

    public ImportTaskQueryController(ImportTaskService importTaskService) {
        this.importTaskService = importTaskService;
    }

    @GetMapping("/{taskNo}")
    public ApiResult<ImportTask> get(@PathVariable String taskNo) {
        return ApiResult.ok(importTaskService.get(taskNo));
    }

    @PostMapping("/{taskNo}/status")
    public ApiResult<ImportTask> move(@PathVariable String taskNo,
                                      @RequestBody MoveImportTaskRequest request) {
        return ApiResult.ok(importTaskService.move(taskNo,
                ImportStatus.valueOf(request.getStatus()), request.getIssueCount()));
    }
}
