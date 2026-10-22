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
    default CalculationResult getVersionForUser(String batchNo, String versionNo, String userId) {
        throw new IllegalStateException("当前存储不支持历史结果");
    }
    CalculationResult confirm(String batchNo, String operator);
    CalculationResult publish(String batchNo, String operator);
}
