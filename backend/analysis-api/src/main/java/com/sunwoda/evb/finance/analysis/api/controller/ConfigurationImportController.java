package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.CreateConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.api.dto.MoveConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config/imports")
public class ConfigurationImportController {
    private final ConfigurationImportService service;

    public ConfigurationImportController(ConfigurationImportService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<ConfigurationImportTask> create(@RequestBody CreateConfigurationImportRequest request) {
        return ApiResult.ok(service.create(ConfigurationImportType.valueOf(request.getType()),
                request.getFileName(), request.getChecksum(), request.getCreatedBy()));
    }

    @GetMapping("/{taskNo}")
    public ApiResult<ConfigurationImportTask> get(@PathVariable String taskNo) {
        return ApiResult.ok(service.get(taskNo));
    }

    @PostMapping("/{taskNo}/status")
    public ApiResult<ConfigurationImportTask> move(@PathVariable String taskNo,
                                                  @RequestBody MoveConfigurationImportRequest request) {
        return ApiResult.ok(service.move(taskNo, ImportStatus.valueOf(request.getStatus())));
    }
}
