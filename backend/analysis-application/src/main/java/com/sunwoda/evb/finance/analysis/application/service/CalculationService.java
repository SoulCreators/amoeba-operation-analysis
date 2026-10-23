package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;

public interface CalculationService {
    // 本次修改：无操作者入口失败关闭，避免内部调用在没有认证身份时绕过授权。
    default CalculationResult calculate(String batchNo) {
        throw new IllegalStateException("计算必须提供认证操作者");
    }
    // 本次新增：为计算补充可信操作者入口，供应用层执行总部财经权限校验。
    default CalculationResult calculate(String batchNo, String operator) {
        throw new UnsupportedOperationException("带操作者的计算入口尚未由实现提供");
    }
    CalculationResult get(String batchNo);
    CalculationResult getForUser(String batchNo, String userId);
    /**
     * 累计口径：按当年 1 月至目标批次所在月，汇总同视角各期间批次的事实后重算损益行。
     * 结果为只读派生值，不落库、不覆盖已发布结果；权限与范围过滤复用当期读取口径。
     */
    default CalculationResult getCumulativeForUser(String batchNo, String userId) {
        throw new UnsupportedOperationException("累计口径尚未由实现提供");
    }
    default CalculationResult getVersionForUser(String batchNo, String versionNo, String userId) {
        throw new IllegalStateException("当前存储不支持历史结果");
    }
    CalculationResult confirm(String batchNo, String operator);
    CalculationResult publish(String batchNo, String operator);
    // 版本撤回：仅已发布结果可撤回，记录原因与通知对象；金额与指标保持不变。
    default CalculationResult withdraw(String batchNo, String reason,
                                       java.util.List<String> notificationTargets, String operator) {
        throw new UnsupportedOperationException("版本撤回尚未由实现提供");
    }
}
