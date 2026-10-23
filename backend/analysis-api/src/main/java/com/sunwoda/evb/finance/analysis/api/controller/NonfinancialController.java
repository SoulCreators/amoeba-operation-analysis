package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.ImportNonfinancialFactsRequest;
import com.sunwoda.evb.finance.analysis.application.model.NonfinancialCalculationSummary;
import com.sunwoda.evb.finance.analysis.application.service.NonfinancialMetricService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.TargetActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * 非财经指标（电芯优率）接口：长表导入、目标/实际配对计算、服务端行级授权查询与导出。
 */
@RestController
@RequestMapping("/api/v1")
public class NonfinancialController {
    private final NonfinancialMetricService nonfinancialMetricService;

    public NonfinancialController(NonfinancialMetricService nonfinancialMetricService) {
        this.nonfinancialMetricService = nonfinancialMetricService;
    }

    @PostMapping("/batches/{batchNo}/nonfinancial/facts")
    public ApiResult<Integer> importFacts(@PathVariable String batchNo,
                                          @RequestBody ImportNonfinancialFactsRequest request,
                                          Principal principal) {
        String uploadedBy = requiredUser(principal);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("导入事实不能为空");
        }
        List<NonfinancialFact> facts = new ArrayList<NonfinancialFact>(request.getItems().size());
        for (ImportNonfinancialFactsRequest.Item item : request.getItems()) {
            facts.add(NonfinancialFact.item(item.getBaseCode(), item.getProductionLine(),
                    item.getCellModel(), item.getQualityCategory(), parseTargetActual(item.getTargetActual()),
                    item.getQualityValue(), item.getSourceRowNo(), item.getEffectiveFileVersion()));
        }
        return ApiResult.ok(nonfinancialMetricService.importFacts(batchNo, facts, uploadedBy));
    }

    @PostMapping("/batches/{batchNo}/nonfinancial/calculate")
    public ApiResult<NonfinancialCalculationSummary> calculate(@PathVariable String batchNo,
                                                               Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(nonfinancialMetricService.calculate(batchNo));
    }

    @GetMapping("/nonfinancial/results")
    public ApiResult<List<NonfinancialResult>> listVisible(@RequestParam String period,
                                                           Principal principal) {
        String userId = requiredUser(principal);
        return ApiResult.ok(nonfinancialMetricService.listVisible(period, userId));
    }

    @GetMapping("/nonfinancial/results/export")
    public ApiResult<List<NonfinancialResult>> export(@RequestParam String period,
                                                      Principal principal) {
        String userId = requiredUser(principal);
        return ApiResult.ok(nonfinancialMetricService.listVisible(period, userId));
    }

    @PostMapping("/nonfinancial/results/{period}/publish")
    public ApiResult<NonfinancialPublication> publish(@PathVariable String period,
                                                      Principal principal) {
        String operator = requiredUser(principal);
        return ApiResult.ok(nonfinancialMetricService.publish(period, operator));
    }

    @GetMapping("/nonfinancial/results/{period}/publication")
    public ApiResult<NonfinancialPublication> getPublication(@PathVariable String period,
                                                             Principal principal) {
        requiredUser(principal);
        return ApiResult.ok(nonfinancialMetricService.getPublication(period));
    }

    private TargetActual parseTargetActual(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("目标/实际标识不能为空");
        }
        try {
            return TargetActual.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("目标/实际标识无效: " + value);
        }
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName();
    }
}
