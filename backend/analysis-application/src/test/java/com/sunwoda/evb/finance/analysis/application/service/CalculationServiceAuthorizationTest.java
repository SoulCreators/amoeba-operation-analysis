package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.model.PnlResult;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculationServiceAuthorizationTest {
    @Test
    void nonFinanceCannotReadUnpublishedResult() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.CALCULATED));
        Scopes scopes = new Scopes();
        scopes.save(scope("U-OPS", "BASE_OPS", "NANCHANG"));

        CalculationService service = service(results, scopes);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getForUser("B-AUTH", "U-OPS"));

        assertEquals("当前结果尚未发布，仅财经角色可查看草稿", error.getMessage());
    }

    @Test
    void financeCanReadDraftButOnlyWithinAuthorizedScopeAndFacts() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.CALCULATED));
        Scopes scopes = new Scopes();
        scopes.save(scope("U-FIN", "BASE_FINANCE", "NANCHANG"));

        CalculationResult visible = service(results, scopes).getForUser("B-AUTH", "U-FIN");

        assertEquals(1, visible.getResults().size());
        assertEquals("NANCHANG", visible.getResults().get(0).getScopeCode());
        assertEquals(1, visible.getFacts().size());
        assertEquals("NANCHANG", visible.getFacts().get(0).getScopeCode());
    }

    @Test
    void publishedResultIsStillFilteredForNonFinanceUser() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.PUBLISHED));
        Scopes scopes = new Scopes();
        scopes.save(scope("U-OPS", "BASE_OPS", "YICHANG"));

        CalculationResult visible = service(results, scopes).getForUser("B-AUTH", "U-OPS");

        assertEquals(1, visible.getResults().size());
        assertEquals("YICHANG", visible.getResults().get(0).getScopeCode());
        assertEquals(1, visible.getFacts().size());
        assertEquals("YICHANG", visible.getFacts().get(0).getScopeCode());
    }

    @Test
    void allScopeSeesAllPublishedResults() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.PUBLISHED));
        Scopes scopes = new Scopes();
        scopes.save(scope("U-ADMIN", "ADMIN", "ALL"));

        CalculationResult visible = service(results, scopes).getForUser("B-AUTH", "U-ADMIN");

        assertEquals(2, visible.getResults().size());
        assertEquals(2, visible.getFacts().size());
    }

    @Test
    void disabledScopeCannotExposePublishedResult() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.PUBLISHED));
        Scopes scopes = new Scopes();
        scopes.save(new UserScope("U-OPS", "BASE_OPS", AnalysisPerspective.BASE,
                "NANCHANG", true, true, false, false));

        assertThrows(IllegalStateException.class,
                () -> service(results, scopes).getForUser("B-AUTH", "U-OPS"));
    }

    @Test
    void nonPublishRoleCannotWithdrawPublishedResult() {
        FixedResults results = new FixedResults();
        results.save(result(CalculationResultStatus.PUBLISHED));
        Scopes scopes = new Scopes();
        scopes.save(scope("U-OPS", "BASE_OPS", "NANCHANG"));

        CalculationService service = service(results, scopes);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.withdraw("B-AUTH", "数据口径错误",
                        Collections.singletonList("finance-lead@example.com"), "U-OPS"));

        assertEquals("当前用户无结果撤回权限", error.getMessage());
    }

    private static CalculationService service(FixedResults results, Scopes scopes) {
        PnlFactProvider provider = batch -> Collections.<PnlFact>emptyList();
        return new CalculationServiceImpl(new EmptyBatches(), provider, scopes, results);
    }

    private static CalculationResult result(CalculationResultStatus status) {
        Map<String, BigDecimal> lines = new HashMap<String, BigDecimal>();
        lines.put("REVENUE", BigDecimal.TEN);
        return new CalculationResult("B-AUTH", AnalysisPerspective.BASE,
                Arrays.asList(new PnlResult("NANCHANG", lines), new PnlResult("YICHANG", lines)),
                "B-AUTH-R1", status, null, null, LocalDateTime.now(), null, null,
                Arrays.asList(fact("NANCHANG"), fact("YICHANG")));
    }

    private static PnlFact fact(String scopeCode) {
        return new PnlFact(scopeCode, decimal(1), decimal(10), decimal(7),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

    private static UserScope scope(String userId, String role, String scopeCode) {
        return new UserScope(userId, role, AnalysisPerspective.BASE, scopeCode,
                true, true, false, true);
    }

    private static class FixedResults implements CalculationResultRepository {
        private final Map<String, CalculationResult> data = new HashMap<String, CalculationResult>();

        @Override
        public CalculationResult save(CalculationResult result) {
            data.put(result.getBatchNo(), result);
            return result;
        }

        @Override
        public Optional<CalculationResult> findByBatchNo(String batchNo) {
            return Optional.ofNullable(data.get(batchNo));
        }
    }

    private static class Scopes implements UserScopeRepository {
        private final List<UserScope> data = new ArrayList<UserScope>();

        @Override
        public UserScope save(UserScope scope) {
            data.add(scope);
            return scope;
        }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective) {
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope scope : data) {
                if (scope.isEnabled() && scope.getUserId().equals(userId)
                        && scope.getPerspective() == perspective) {
                    result.add(scope);
                }
            }
            return result;
        }
    }

    private static class EmptyBatches implements AnalysisBatchRepository {
        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.empty();
        }
    }
}
