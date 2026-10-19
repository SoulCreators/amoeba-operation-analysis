package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiAnalysisServiceImplTest {
    @Test
    void rejectsCalculatedResultBeforeCallingAiGateway() {
        AtomicBoolean gatewayCalled = new AtomicBoolean(false);
        LocalAiGateway gateway = result -> {
            gatewayCalled.set(true);
            return Collections.emptyMap();
        };
        AiAnalysisServiceImpl service = new AiAnalysisServiceImpl(
                new StubCalculationService(calculatedResult()), gateway,
                new EmptyBatchRepository(), new EmptyUserScopeRepository());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.generate("B-AI", "U001"));

        assertEquals("只有已确认或已发布结果可以生成AI分析: CALCULATED", error.getMessage());
        assertFalse(gatewayCalled.get());
    }

    private static CalculationResult calculatedResult() {
        return new CalculationResult("B-AI", AnalysisPerspective.BASE,
                Collections.emptyList(), "B-AI-R1", CalculationResultStatus.CALCULATED,
                null, null, LocalDateTime.now(), null, null);
    }

    private static class StubCalculationService implements CalculationService {
        private final CalculationResult result;

        private StubCalculationService(CalculationResult result) {
            this.result = result;
        }

        @Override public CalculationResult calculate(String batchNo) { return result; }
        @Override public CalculationResult get(String batchNo) { return result; }
        @Override public CalculationResult getForUser(String batchNo, String userId) { return result; }
        @Override public CalculationResult confirm(String batchNo, String operator) { return result; }
        @Override public CalculationResult publish(String batchNo, String operator) { return result; }
    }

    private static class EmptyBatchRepository implements AnalysisBatchRepository {
        @Override public AnalysisBatch save(AnalysisBatch batch) { return batch; }
        @Override public Optional<AnalysisBatch> findByBatchNo(String batchNo) { return Optional.empty(); }
    }

    private static class EmptyUserScopeRepository implements UserScopeRepository {
        @Override public UserScope save(UserScope scope) { return scope; }
        @Override public List<UserScope> findByUserAndPerspective(String userId,
                                                                   AnalysisPerspective perspective) {
            return Collections.emptyList();
        }
    }
}
