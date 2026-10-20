package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationResultRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BLOCK-003 regression tests for write authorization at the application-service boundary.
 *
 * <p>The tests call {@link CalculationServiceImpl} directly so a controller/operator
 * parameter cannot be mistaken for authorization. The expected failures are intentional
 * until the service layer enforces the existing FINANCE/ADMIN write contract.</p>
 */
@Tag("BLOCK-003")
class Block003AuthorizationRegressionTest {
    private static final String BATCH_NO = "B-BLOCK-003";

    @Test
    void unmappedUserIsDeniedByDefault() {
        Fixture fixture = fixture(CalculationResultStatus.CALCULATED, BatchStatus.CALCULATED);

        assertThrows(IllegalStateException.class,
                () -> fixture.service().getForUser(BATCH_NO, "U-NOT-GRANTED"));
    }

    @Test
    void existingFinanceAndAdminReadAllowancesRemainAvailable() {
        Fixture financeFixture = fixture(
                CalculationResultStatus.CALCULATED,
                BatchStatus.CALCULATED,
                grant("U-FINANCE", "BASE_FINANCE"));
        assertEquals(1, financeFixture.service().getForUser(BATCH_NO, "U-FINANCE")
                .getResults().size());

        Fixture adminFixture = fixture(
                CalculationResultStatus.PUBLISHED,
                BatchStatus.PUBLISHED,
                grant("U-ADMIN", "ADMIN"));
        assertEquals(1, adminFixture.service().getForUser(BATCH_NO, "U-ADMIN")
                .getResults().size());
    }

    @Disabled("BLOCK-003 待业务 Owner 确认正式生命周期写权限矩阵后实现")
    @Test
    void nonFinanceCannotConfirmThroughApplicationService() {
        Fixture fixture = fixture(
                CalculationResultStatus.CALCULATED,
                BatchStatus.CALCULATED,
                grant("U-OPS", "BASE_OPS"));

        assertThrows(IllegalStateException.class,
                () -> fixture.service().confirm(BATCH_NO, "U-OPS"));
        assertEquals(CalculationResultStatus.CALCULATED, fixture.resultRepository.current().getStatus());
        assertEquals(BatchStatus.CALCULATED, fixture.batchRepository.current().getStatus());
    }

    @Disabled("BLOCK-003 待业务 Owner 确认正式生命周期写权限矩阵后实现")
    @Test
    void nonFinanceCannotPublishThroughApplicationService() {
        Fixture fixture = fixture(
                CalculationResultStatus.CONFIRMED,
                BatchStatus.CONFIRMED,
                grant("U-OPS", "BASE_OPS"));

        assertThrows(IllegalStateException.class,
                () -> fixture.service().publish(BATCH_NO, "U-OPS"));
        assertEquals(CalculationResultStatus.CONFIRMED, fixture.resultRepository.current().getStatus());
        assertEquals(BatchStatus.CONFIRMED, fixture.batchRepository.current().getStatus());
    }

    @Disabled("BLOCK-003 待业务 Owner 确认正式生命周期写权限矩阵后实现")
    @Test
    void existingFinanceAndAdminWriteAllowancesRemainAvailable() {
        Fixture fixture = fixture(
                CalculationResultStatus.CALCULATED,
                BatchStatus.CALCULATED,
                grant("U-FINANCE", "BASE_FINANCE"),
                grant("U-ADMIN", "ADMIN"));

        CalculationResult confirmed = fixture.service().confirm(BATCH_NO, "U-FINANCE");
        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("U-FINANCE", confirmed.getConfirmedBy());

        CalculationResult published = fixture.service().publish(BATCH_NO, "U-ADMIN");
        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals("U-ADMIN", published.getPublishedBy());
    }

    @Disabled("TEST GAP: calculate(String batchNo) has no operator/user identity; do not invent calculate authorization semantics")
    @Test
    void calculateAuthorizationRequiresAnExplicitOperatorContract() {
        // Deliberately disabled until CalculationService.calculate carries a trusted actor
        // and the role required to calculate is confirmed. This is a recorded test gap.
    }

    private static Fixture fixture(CalculationResultStatus resultStatus,
                                   BatchStatus batchStatus,
                                   UserScope... grants) {
        InMemoryBatchRepository batches = new InMemoryBatchRepository();
        batches.save(new AnalysisBatch(1L, BATCH_NO, "2026-09", AnalysisPerspective.BASE,
                batchStatus, "system", LocalDateTime.now()));

        InMemoryResultRepository results = new InMemoryResultRepository();
        results.save(result(resultStatus));

        InMemoryScopeRepository scopes = new InMemoryScopeRepository();
        for (UserScope grant : grants) scopes.save(grant);

        PermissionService permissions = new PermissionServiceImpl(scopes);
        PnlFactProvider facts = batch -> Collections.emptyList();
        CalculationServiceImpl service = new CalculationServiceImpl(
                batches, facts, permissions, results);
        return new Fixture(service, batches, results);
    }

    private static CalculationResult result(CalculationResultStatus status) {
        Map<String, java.math.BigDecimal> lines = new HashMap<String, java.math.BigDecimal>();
        lines.put("VOLUME", java.math.BigDecimal.ONE);
        lines.put("BUDGET_VOLUME", java.math.BigDecimal.ONE);
        lines.put("REVENUE", java.math.BigDecimal.TEN);
        lines.put("BUDGET_REVENUE", java.math.BigDecimal.TEN);
        lines.put("SALES_COST", java.math.BigDecimal.valueOf(7L));
        lines.put("BUDGET_SALES_COST", java.math.BigDecimal.valueOf(7L));
        return new CalculationResult(BATCH_NO, AnalysisPerspective.BASE,
                Collections.singletonList(new com.sunwoda.evb.finance.analysis.domain.model.PnlResult(
                        "NANCHANG", lines)),
                BATCH_NO + "-R1", status, null, null,
                LocalDateTime.now(), null, null, Collections.emptyList());
    }

    private static UserScope grant(String userId, String roleCode) {
        return new UserScope(userId, roleCode, AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true);
    }

    private static final class Fixture {
        private final CalculationServiceImpl service;
        private final InMemoryBatchRepository batchRepository;
        private final InMemoryResultRepository resultRepository;

        private Fixture(CalculationServiceImpl service,
                        InMemoryBatchRepository batchRepository,
                        InMemoryResultRepository resultRepository) {
            this.service = service;
            this.batchRepository = batchRepository;
            this.resultRepository = resultRepository;
        }

        private CalculationServiceImpl service() {
            return service;
        }
    }

    private static final class InMemoryBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> data = new HashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            data.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(data.get(batchNo));
        }

        private AnalysisBatch current() {
            return data.get(BATCH_NO);
        }
    }

    private static final class InMemoryResultRepository implements CalculationResultRepository {
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

        private CalculationResult current() {
            return data.get(BATCH_NO);
        }
    }

    private static final class InMemoryScopeRepository implements UserScopeRepository {
        private final List<UserScope> data = new ArrayList<UserScope>();

        @Override
        public UserScope save(UserScope scope) {
            data.add(scope);
            return scope;
        }

        @Override
        public List<UserScope> findByUserAndPerspective(String userId,
                                                         AnalysisPerspective perspective) {
            List<UserScope> result = new ArrayList<UserScope>();
            for (UserScope scope : data) {
                if (scope.isEnabled()
                        && scope.getUserId().equals(userId)
                        && scope.getPerspective() == perspective) {
                    result.add(scope);
                }
            }
            return result;
        }
    }
}
