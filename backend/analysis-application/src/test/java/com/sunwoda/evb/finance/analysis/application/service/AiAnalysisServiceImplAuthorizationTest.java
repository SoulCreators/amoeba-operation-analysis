package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAnalysisServiceImplAuthorizationTest {
    @Test
    void rejectsGenerationForCalculatedResultBeforeCallingAiGateway() {
        final int[] gatewayCalls = {0};
        AiAnalysisServiceImpl service = service(CalculationResultStatus.CALCULATED, gatewayCalls);

        assertThrows(IllegalStateException.class,
                () -> service.generate("B-AI", "U-FIN"));
        assertEquals(0, gatewayCalls[0]);
    }

    @Test
    void allowsGenerationForConfirmedResult() {
        final int[] gatewayCalls = {0};
        AiAnalysisDraft draft = service(CalculationResultStatus.CONFIRMED, gatewayCalls)
                .generate("B-AI", "U-FIN");

        assertEquals(AiAnalysisDraftStatus.DRAFT, draft.getStatus());
        assertEquals("B-AI-R1", draft.getVersionNo());
        assertEquals(1, gatewayCalls[0]);
    }

    @Test
    void allowsGenerationForPublishedResult() {
        final int[] gatewayCalls = {0};
        AiAnalysisDraft draft = service(CalculationResultStatus.PUBLISHED, gatewayCalls)
                .generate("B-AI", "U-FIN");

        assertEquals(AiAnalysisDraftStatus.DRAFT, draft.getStatus());
        assertTrue(draft.getSections().containsKey("summary"));
        assertEquals(1, gatewayCalls[0]);
    }

    private static AiAnalysisServiceImpl service(CalculationResultStatus status, int[] gatewayCalls) {
        CalculationService calculationService = new FixedCalculationService(result(status));
        LocalAiGateway gateway = result -> {
            gatewayCalls[0]++;
            return Collections.singletonMap("summary", "generated");
        };
        return new AiAnalysisServiceImpl(calculationService, gateway,
                new EmptyBatchRepository(), new EmptyUserScopeRepository());
    }

    private static CalculationResult result(CalculationResultStatus status) {
        return new CalculationResult("B-AI", AnalysisPerspective.BASE,
                Collections.<PnlResult>emptyList(), "B-AI-R1", status,
                null, null, LocalDateTime.now(), null, null);
    }

    private static class FixedCalculationService implements CalculationService {
        private final CalculationResult result;

        private FixedCalculationService(CalculationResult result) {
            this.result = result;
        }

        @Override
        public CalculationResult calculate(String batchNo) { return result; }

        @Override
        public CalculationResult get(String batchNo) { return result; }

        @Override
        public CalculationResult getForUser(String batchNo, String userId) { return result; }

        @Override
        public CalculationResult confirm(String batchNo, String operator) { return result; }

        @Override
        public CalculationResult publish(String batchNo, String operator) { return result; }
    }

    private static class EmptyBatchRepository implements AnalysisBatchRepository {
        @Override
        public AnalysisBatch save(AnalysisBatch batch) { return batch; }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.empty();
        }
    }

    private static class EmptyUserScopeRepository implements UserScopeRepository {
        @Override
        public UserScope save(UserScope scope) { return scope; }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective) {
            return Collections.emptyList();
        }
    }
}
