package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.port.DatasetCatalog;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.DatasetDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {
    private final DatasetCatalog catalog;

    public DatasetController(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ApiResult<List<DatasetDefinition>> list(@RequestParam String perspective) {
        return ApiResult.ok(catalog.list(AnalysisPerspective.fromCode(perspective)));
    }
}
