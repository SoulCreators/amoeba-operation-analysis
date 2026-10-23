package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AdjustmentRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 调整单应用服务：授权、状态机与范围裁剪。 */
class AdjustmentServiceImplTest {
    @Test
    void createActivatesAdjustmentAndPersistsIt() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));

        Adjustment created = service.create("2026-09", AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-HQ");

        assertEquals(AdjustmentStatus.ACTIVE, created.getStatus());
        assertEquals("U-HQ", created.getEnteredBy());
        assertTrue(repository.findById(created.getAdjustmentId()).isPresent());
        assertEquals(AdjustmentStatus.ACTIVE, repository.findById(created.getAdjustmentId()).get().getStatus());
    }

    @Test
    void createRejectsUserWithoutHeadquartersFinanceGrant() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, scope("U-BASE", "BASE_FINANCE", "NANCHANG"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.create("2026-09", AnalysisPerspective.BASE, "NANCHANG",
                        AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                        AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-BASE"));

        assertEquals("当前用户无调整录入权限", error.getMessage());
    }

    @Test
    void voidAdjustmentTransitionsActiveToVoidWithTrace() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));
        Adjustment created = service.create("2026-09", AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-HQ");

        Adjustment voided = service.voidAdjustment(created.getAdjustmentId(), "政策变更", "U-HQ");

        assertEquals(AdjustmentStatus.VOID, voided.getStatus());
        assertEquals("U-HQ", voided.getVoidedBy());
        assertEquals("政策变更", voided.getVoidReason());
    }

    @Test
    void voidAdjustmentRejectsBlankReason() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));
        Adjustment created = service.create("2026-09", AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-HQ");

        assertThrows(IllegalArgumentException.class,
                () -> service.voidAdjustment(created.getAdjustmentId(), "  ", "U-HQ"));
    }

    @Test
    void voidAdjustmentRejectsUnknownAdjustmentId() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.voidAdjustment("ADJ-MISSING", "政策变更", "U-HQ"));

        assertEquals("调整不存在: ADJ-MISSING", error.getMessage());
    }

    @Test
    void listFiltersByViewableScopes() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));
        repository.save(active("ADJ-NC", "NANCHANG"));
        repository.save(active("ADJ-YC", "YICHANG"));

        List<Adjustment> visible = service.list("2026-09", AnalysisPerspective.BASE, "U-HQ");

        assertEquals(1, visible.size());
        assertEquals("NANCHANG", visible.get(0).getScopeCode());
    }

    private static Adjustment active(String adjustmentId, String scopeCode) {
        return Adjustment.create(adjustmentId, "2026-09", AnalysisPerspective.BASE, scopeCode,
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-HQ",
                java.time.LocalDateTime.now()).activate();
    }

    @Test
    void listActiveReturnsOnlyActiveForPeriodAndPerspective() {
        FakeAdjustmentRepository repository = new FakeAdjustmentRepository();
        AdjustmentServiceImpl service = service(repository, hqScope("U-HQ", "NANCHANG"));
        Adjustment active = service.create("2026-09", AnalysisPerspective.BASE, "NANCHANG",
                AdjustmentScenario.ACTUAL, "REVENUE", new BigDecimal("100.00"),
                AdjustmentDirection.INCREASE, "口径补充", "ev-1", "R1", "U-HQ");
        service.voidAdjustment(active.getAdjustmentId(), "政策变更", "U-HQ");

        List<Adjustment> activeOnly = service.listActive("2026-09", AnalysisPerspective.BASE);

        assertEquals(0, activeOnly.size());
    }

    private static AdjustmentServiceImpl service(FakeAdjustmentRepository repository,
                                                UserScope scope) {
        UserScopeRepository scopes = new FakeUserScopeRepository(scope);
        return new AdjustmentServiceImpl(repository, new PermissionServiceImpl(scopes));
    }

    private static UserScope hqScope(String userId, String scopeCode) {
        return new UserScope(userId, "HQ_FINANCE", AnalysisPerspective.BASE, scopeCode,
                true, true, false, true);
    }

    private static UserScope scope(String userId, String role, String scopeCode) {
        return new UserScope(userId, role, AnalysisPerspective.BASE, scopeCode,
                true, true, false, true);
    }

    private static class FakeAdjustmentRepository implements AdjustmentRepository {
        private final Map<String, Adjustment> data = new HashMap<String, Adjustment>();

        @Override
        public Adjustment save(Adjustment adjustment) {
            data.put(adjustment.getAdjustmentId(), adjustment);
            return adjustment;
        }

        @Override
        public Optional<Adjustment> findById(String adjustmentId) {
            return Optional.ofNullable(data.get(adjustmentId));
        }

        @Override
        public List<Adjustment> findByPeriod(String period) {
            List<Adjustment> result = new ArrayList<Adjustment>();
            for (Adjustment adjustment : data.values()) {
                if (adjustment.getPeriod().equals(period)) result.add(adjustment);
            }
            return result;
        }

        @Override
        public List<Adjustment> findActive(String period, AnalysisPerspective perspective) {
            List<Adjustment> result = new ArrayList<Adjustment>();
            for (Adjustment adjustment : data.values()) {
                if (adjustment.isActive() && adjustment.getPeriod().equals(period)
                        && adjustment.getPerspective() == perspective) {
                    result.add(adjustment);
                }
            }
            return result;
        }
    }

    private static class FakeUserScopeRepository implements UserScopeRepository {
        private final List<UserScope> data = new ArrayList<UserScope>();

        FakeUserScopeRepository(UserScope scope) {
            data.add(scope);
        }

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
}
