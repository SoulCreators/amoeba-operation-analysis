package com.sunwoda.evb.finance.analysis.application.adapter;

import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;

/**
 * 既有计算服务的失败处理执行适配器。
 *
 * <p>本次新增：只调用现有 CalculationService 接口，不修改其实现文件和计算逻辑。</p>
 */
public final class CalculationServiceRetryExecutor implements CalculationRetryExecutor {
    private final CalculationService calculationService;

    public CalculationServiceRetryExecutor(CalculationService calculationService) {
        if (calculationService == null) throw new IllegalArgumentException("calculationService 不能为空");
        this.calculationService = calculationService;
    }

    @Override
    public void execute(String batchNo) {
        calculationService.calculate(required(batchNo));
    }

    private String required(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) throw new IllegalArgumentException("batchNo 不能为空");
        return batchNo.trim();
    }
}
