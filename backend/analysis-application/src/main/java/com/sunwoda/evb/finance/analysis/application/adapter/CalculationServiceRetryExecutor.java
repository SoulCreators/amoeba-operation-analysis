package com.sunwoda.evb.finance.analysis.application.adapter;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 既有计算服务的失败处理执行适配器。
 *
 * <p>本次修复：禁止自动重试调用无操作者计算入口；由显式内部身份调用带操作者重载，
 * 并在调用前追加审计事件，身份授权不足时安全失败关闭。</p>
 */
public final class CalculationServiceRetryExecutor implements CalculationRetryExecutor {
    /** 本次修复：自动重试使用可配置、可审计且必须在 IAM 中显式授权的内部身份。 */
    public static final String DEFAULT_INTERNAL_RETRY_OPERATOR = "SYSTEM_RETRY_WORKER";
    private final CalculationService calculationService;
    private final AuditEventPort auditEventPort;
    private final String operator;

    public CalculationServiceRetryExecutor(CalculationService calculationService,
                                           AuditEventPort auditEventPort,
                                           String operator) {
        if (calculationService == null) throw new IllegalArgumentException("calculationService 不能为空");
        if (auditEventPort == null) throw new IllegalArgumentException("auditEventPort 不能为空");
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("内部重试身份不能为空");
        }
        this.calculationService = calculationService;
        this.auditEventPort = auditEventPort;
        this.operator = operator.trim();
    }

    @Override
    public void execute(String batchNo) {
        String normalizedBatchNo = required(batchNo);
        String traceId = "retry-" + UUID.randomUUID().toString();
        // 本次修复：先记录内部身份、批次和追踪号，再调用带操作者的计算入口，避免无审计执行。
        auditEventPort.append(new AuditEvent(
                "retry-attempt-" + UUID.randomUUID().toString(),
                traceId,
                operator,
                "analysis-worker",
                "CALCULATION_RETRY_EXECUTION",
                "CALCULATION_BATCH",
                normalizedBatchNo,
                "PENDING_RETRY",
                "PROCESSING",
                "STARTED",
                "自动重试内部身份已审计",
                "{}",
                LocalDateTime.now()));
        // 本次修复：必须走带 operator 的权限入口；SYSTEM_RETRY_WORKER 未配置总部财经授权时拒绝执行。
        calculationService.calculate(normalizedBatchNo, operator);
    }

    private String required(String batchNo) {
        if (batchNo == null || batchNo.trim().isEmpty()) throw new IllegalArgumentException("batchNo 不能为空");
        return batchNo.trim();
    }
}
