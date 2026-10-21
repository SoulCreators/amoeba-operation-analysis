package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.adapter.CalculationServiceRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RETRY-002 内部身份接线测试。
 *
 * <p>本次新增测试：锁定自动重试必须调用带操作者的计算入口，并在调用前写入身份审计事件。</p>
 */
class CalculationServiceRetryExecutorTest {
    @Test
    void usesExplicitOperatorAwareCalculationAndAuditsBeforeInvocation() {
        RecordingCalculationService calculationService = new RecordingCalculationService();
        RecordingAudit audit = new RecordingAudit();

        new CalculationServiceRetryExecutor(
                calculationService, audit, CalculationServiceRetryExecutor.DEFAULT_INTERNAL_RETRY_OPERATOR)
                .execute("BATCH-RETRY-001");

        assertEquals("BATCH-RETRY-001", calculationService.batchNo);
        assertEquals(CalculationServiceRetryExecutor.DEFAULT_INTERNAL_RETRY_OPERATOR,
                calculationService.operator);
        assertEquals(1, audit.events.size());
        assertEquals(CalculationServiceRetryExecutor.DEFAULT_INTERNAL_RETRY_OPERATOR,
                audit.events.get(0).getOperator());
        assertEquals("STARTED", audit.events.get(0).getResult());
    }

    @Test
    void rejectsMissingInternalOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> new CalculationServiceRetryExecutor(
                        new RecordingCalculationService(), new RecordingAudit(), " "));
    }

    private static final class RecordingCalculationService implements CalculationService {
        private String batchNo;
        private String operator;

        @Override
        public CalculationResult calculate(String batchNo) {
            throw new AssertionError("自动重试不得调用无操作者计算入口");
        }

        @Override
        public CalculationResult calculate(String batchNo, String operator) {
            this.batchNo = batchNo;
            this.operator = operator;
            return null;
        }

        @Override public CalculationResult get(String batchNo) { return null; }
        @Override public CalculationResult getForUser(String batchNo, String userId) { return null; }
        @Override public CalculationResult confirm(String batchNo, String operator) { return null; }
        @Override public CalculationResult publish(String batchNo, String operator) { return null; }
    }

    private static final class RecordingAudit implements AuditEventPort {
        private final List<AuditEvent> events = new ArrayList<AuditEvent>();

        @Override
        public void append(AuditEvent event) {
            events.add(event);
        }
    }
}
