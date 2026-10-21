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
 * <p>本次修改：测试直接调用 {@link CalculationServiceImpl}，锁定总部财经写权限和
 * ADMIN 只读边界，避免把控制器传入的操作者参数误当成授权本身。</p>
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

    @Test
    void adminReadOnlyRoleCannotConfirmOrPublishThroughApplicationService() {
        Fixture confirmFixture = fixture(
                CalculationResultStatus.CALCULATED,
                BatchStatus.CALCULATED,
                grant("U-ADMIN", "ADMIN"));
        Fixture publishFixture = fixture(
                CalculationResultStatus.CONFIRMED,
                BatchStatus.CONFIRMED,
                grant("U-ADMIN", "ADMIN"));

        // 本次新增：锁定 ADMIN 的只读语义不能在应用服务边界升级为确认或发布写权限。
        assertThrows(IllegalStateException.class,
                () -> confirmFixture.service().confirm(BATCH_NO, "U-ADMIN"));
        assertThrows(IllegalStateException.class,
                () -> publishFixture.service().publish(BATCH_NO, "U-ADMIN"));
        assertEquals(CalculationResultStatus.CALCULATED,
                confirmFixture.resultRepository.current().getStatus());
        assertEquals(CalculationResultStatus.CONFIRMED,
                publishFixture.resultRepository.current().getStatus());
    }

    @Test
    void headquartersFinanceCanConfirmAndPublishAsTheSameActor() {
        Fixture fixture = fixture(
                CalculationResultStatus.CALCULATED,
                BatchStatus.CALCULATED,
                grant("U-HQ-FINANCE", "HQ_FINANCE"));

        // 本次修改：同一总部财经操作者可以连续完成确认和发布，不要求双人分离。
        CalculationResult confirmed = fixture.service().confirm(BATCH_NO, "U-HQ-FINANCE");
        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("U-HQ-FINANCE", confirmed.getConfirmedBy());

        CalculationResult published = fixture.service().publish(BATCH_NO, "U-HQ-FINANCE");
        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals("U-HQ-FINANCE", published.getPublishedBy());
    }

    @Test
    void calculateAuthorizationUsesTheExplicitOperatorContract() {
        Fixture denied = fixture(CalculationResultStatus.CALCULATED, BatchStatus.READY_FOR_CALCULATION,
                grant("U-OPS", "BASE_OPS"));
        assertThrows(IllegalStateException.class,
                () -> denied.service().calculate(BATCH_NO, "U-OPS"));

        Fixture allowed = fixture(CalculationResultStatus.CALCULATED, BatchStatus.READY_FOR_CALCULATION,
                grant("U-HQ-FINANCE", "HQ_FINANCE"));
        // 本次新增：带操作者重载是计算的应用层授权入口。
        assertEquals(CalculationResultStatus.CALCULATED,
                allowed.service().calculate(BATCH_NO, "U-HQ-FINANCE").getStatus());
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
