package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.NonfinancialCalculationSummary;
import com.sunwoda.evb.finance.analysis.application.port.LeaderModelMapping;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialFact;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublicationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialResult;
import com.sunwoda.evb.finance.analysis.domain.model.PairingStatus;
import com.sunwoda.evb.finance.analysis.domain.model.TargetActual;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialFactRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialPublicationRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 非财经指标应用服务契约：长表导入重复阻断、目标/实际配对与巴长授权过滤。 */
class NonfinancialMetricServiceTest {
    private static final String BATCH_NO = "BATCH-NF-001";
    private static final String PERIOD = "2026-10";

    private FakeBatchRepository batchRepository;
    private FakeFactRepository factRepository;
    private FakeResultRepository resultRepository;
    private FakePublicationRepository publicationRepository;
    private FakeLeaderMapping leaderMapping;
    private FakePermissionService permissionService;
    private NonfinancialMetricService service;

    @BeforeEach
    void setUp() {
        batchRepository = new FakeBatchRepository();
        factRepository = new FakeFactRepository();
        resultRepository = new FakeResultRepository();
        publicationRepository = new FakePublicationRepository();
        leaderMapping = new FakeLeaderMapping();
        permissionService = new FakePermissionService();
        service = new NonfinancialMetricServiceImpl(batchRepository, factRepository,
                resultRepository, publicationRepository, leaderMapping, permissionService);
        batchRepository.save(new AnalysisBatch(null, BATCH_NO, PERIOD, AnalysisPerspective.BASE,
                BatchStatus.DRAFT, "u-1", LocalDateTime.now()));
    }

    @Test
    void importFactsBindsPeriodAndReturnsCount() {
        int count = service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "0.9800"),
                actual("BASE-1", "L1", "CELL-1", "0.9700")), "u-1");

        assertEquals(2, count);
        List<NonfinancialFact> stored = factRepository.findByPeriod(PERIOD);
        assertEquals(2, stored.size());
        assertEquals(PERIOD, stored.get(0).getPeriod());
    }

    @Test
    void duplicateBusinessKeyAndTargetActualIsBlocked() {
        assertThrows(IllegalArgumentException.class, () -> service.importFacts(BATCH_NO,
                Arrays.asList(target("BASE-1", "L1", "CELL-1", "0.9800"),
                        target("BASE-1", "L1", "CELL-1", "0.9700")), "u-1"));
    }

    @Test
    void emptyFactsAndUnknownBatchAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importFacts(BATCH_NO, Collections.emptyList(), "u-1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.importFacts("MISSING-BATCH",
                        Collections.singletonList(target("BASE-1", "L1", "CELL-1", "0.98")),
                        "u-1"));
    }

    @Test
    void calculatePairsTargetAndActualAndComputesGapAndRate() {
        service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "1.00"),
                actual("BASE-1", "L1", "CELL-1", "0.50")), "u-1");
        leaderMapping.register("CELL-1", "LDR-1");

        NonfinancialCalculationSummary summary = service.calculate(BATCH_NO);

        assertEquals(1, summary.getTotal());
        assertEquals(0, summary.getUnmatchedCount());
        assertEquals(0, summary.getConflictCount());
        NonfinancialResult result = summary.getResults().get(0);
        assertEquals(PairingStatus.BOTH, result.getPairingStatus());
        assertEquals(0, result.getGap().compareTo(new BigDecimal("-0.50")));
        assertEquals(0, result.getAchievementRate().compareTo(new BigDecimal("0.5000000000")));
        assertEquals("LDR-1", result.getLeaderEmployeeNo());
        assertFalse(result.isLeaderConflict());
    }

    @Test
    void targetOnlyIsUnpairedAndUnmatchedWithoutLeaderMapping() {
        service.importFacts(BATCH_NO,
                Collections.singletonList(target("BASE-1", "L1", "CELL-1", "1.00")), "u-1");

        NonfinancialCalculationSummary summary = service.calculate(BATCH_NO);

        assertEquals(1, summary.getTotal());
        assertEquals(1, summary.getUnmatchedCount());
        NonfinancialResult result = summary.getResults().get(0);
        assertEquals(PairingStatus.TARGET_ONLY, result.getPairingStatus());
        assertNull(result.getGap());
        assertNull(result.getAchievementRate());
        assertNull(result.getLeaderEmployeeNo());
    }

    @Test
    void multipleLeadersProduceConflictThatBlocksPublish() {
        service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "1.00"),
                actual("BASE-1", "L1", "CELL-1", "0.50")), "u-1");
        leaderMapping.register("CELL-1", "LDR-1");
        leaderMapping.register("CELL-1", "LDR-2");

        NonfinancialCalculationSummary summary = service.calculate(BATCH_NO);

        assertEquals(1, summary.getConflictCount());
        assertTrue(summary.hasPublishBlockingConflict());
        assertTrue(summary.getResults().get(0).isLeaderConflict());
        assertNull(summary.getResults().get(0).getLeaderEmployeeNo());
    }

    @Test
    void calculateWithoutFactsIsRejected() {
        assertThrows(IllegalStateException.class, () -> service.calculate(BATCH_NO));
    }

    @Test
    void listVisibleReturnsAllForHeadquartersOrAdmin() {
        importTwoResults();
        permissionService.grantBases("hq-finance", "ALL");

        assertEquals(2, service.listVisible(PERIOD, "hq-finance").size());
    }

    @Test
    void listVisibleFiltersByBaseForBaseFinanceUser() {
        importTwoResults();
        permissionService.grantBases("base-fin", "BASE-1");

        List<NonfinancialResult> visible = service.listVisible(PERIOD, "base-fin");
        assertEquals(1, visible.size());
        assertEquals("BASE-1", visible.get(0).getBaseCode());
    }

    @Test
    void listVisibleFiltersByMappedModelForLeader() {
        importTwoResults();

        List<NonfinancialResult> visible = service.listVisible(PERIOD, "LDR-1");
        assertEquals(1, visible.size());
        assertEquals("CELL-1", visible.get(0).getCellModel());
    }

    @Test
    void listVisibleReturnsEmptyWithoutAnyGrant() {
        importTwoResults();

        assertTrue(service.listVisible(PERIOD, "nobody").isEmpty());
    }

    @Test
    void publishSucceedsWhenNoConflict() {
        service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "1.00"),
                actual("BASE-1", "L1", "CELL-1", "0.50")), "u-1");
        leaderMapping.register("CELL-1", "LDR-1");
        service.calculate(BATCH_NO);

        NonfinancialPublication publication = service.publish(PERIOD, "hq-finance");

        assertEquals(NonfinancialPublicationStatus.PUBLISHED, publication.getStatus());
        assertEquals(0, publication.getConflictCount());
        assertEquals("hq-finance", publication.getPublishedBy());
        assertTrue(publication.isPublished());
        assertTrue(service.getPublication(PERIOD).isPublished());
    }

    @Test
    void publishBlocksOnLeaderConflict() {
        service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "1.00"),
                actual("BASE-1", "L1", "CELL-1", "0.50")), "u-1");
        leaderMapping.register("CELL-1", "LDR-1");
        leaderMapping.register("CELL-1", "LDR-2");
        service.calculate(BATCH_NO);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.publish(PERIOD, "hq-finance"));
        assertEquals("存在多巴长冲突，禁止发布: " + PERIOD, ex.getMessage());
        assertFalse(service.getPublication(PERIOD).isPublished());
    }

    @Test
    void publishWithoutResultsIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.publish(PERIOD, "hq-finance"));
        assertEquals("该期间尚无已计算的非财经结果: " + PERIOD, ex.getMessage());
    }

    @Test
    void publishRequiresOperator() {
        service.importFacts(BATCH_NO, Collections.singletonList(
                target("BASE-1", "L1", "CELL-1", "1.00")), "u-1");
        assertThrows(IllegalArgumentException.class, () -> service.publish(PERIOD, " "));
    }

    private static NonfinancialFact target(String base, String line, String model, String value) {
        return NonfinancialFact.item(base, line, model, "YIELD", TargetActual.TARGET,
                new BigDecimal(value), 1, "fv-1");
    }

    private static NonfinancialFact actual(String base, String line, String model, String value) {
        return NonfinancialFact.item(base, line, model, "YIELD", TargetActual.ACTUAL,
                new BigDecimal(value), 2, "fv-1");
    }

    /** 导入跨两个基地、两个型号的配对事实并计算，供行级授权测试复用。 */
    private void importTwoResults() {
        service.importFacts(BATCH_NO, Arrays.asList(
                target("BASE-1", "L1", "CELL-1", "1.00"),
                actual("BASE-1", "L1", "CELL-1", "0.50"),
                target("BASE-2", "L1", "CELL-2", "1.00"),
                actual("BASE-2", "L1", "CELL-2", "0.60")), "u-1");
        leaderMapping.register("CELL-1", "LDR-1");
        leaderMapping.register("CELL-2", "LDR-2");
        service.calculate(BATCH_NO);
    }

    /** 应用层测试自带的批次仓储。 */
    private static final class FakeBatchRepository implements AnalysisBatchRepository {
        private final Map<String, AnalysisBatch> store = new LinkedHashMap<String, AnalysisBatch>();

        @Override
        public AnalysisBatch save(AnalysisBatch batch) {
            store.put(batch.getBatchNo(), batch);
            return batch;
        }

        @Override
        public Optional<AnalysisBatch> findByBatchNo(String batchNo) {
            return Optional.ofNullable(store.get(batchNo));
        }
    }

    /** 应用层测试自带的非财经事实仓储。 */
    private static final class FakeFactRepository implements NonfinancialFactRepository {
        private final Map<String, List<NonfinancialFact>> byPeriod =
                new LinkedHashMap<String, List<NonfinancialFact>>();

        @Override
        public void replaceByPeriod(String period, List<NonfinancialFact> facts) {
            byPeriod.put(period, new ArrayList<NonfinancialFact>(facts));
        }

        @Override
        public List<NonfinancialFact> findByPeriod(String period) {
            List<NonfinancialFact> found = byPeriod.get(period);
            return found == null ? Collections.<NonfinancialFact>emptyList()
                    : new ArrayList<NonfinancialFact>(found);
        }
    }

    /** 应用层测试自带的非财经结果仓储。 */
    private static final class FakeResultRepository implements NonfinancialResultRepository {
        private final Map<String, List<NonfinancialResult>> byPeriod =
                new LinkedHashMap<String, List<NonfinancialResult>>();

        @Override
        public void replaceByPeriod(String period, List<NonfinancialResult> results) {
            byPeriod.put(period, new ArrayList<NonfinancialResult>(results));
        }

        @Override
        public List<NonfinancialResult> findByPeriod(String period) {
            List<NonfinancialResult> found = byPeriod.get(period);
            return found == null ? Collections.<NonfinancialResult>emptyList()
                    : new ArrayList<NonfinancialResult>(found);
        }
    }

    /** 应用层测试自带的发布记录仓储。 */
    private static final class FakePublicationRepository implements NonfinancialPublicationRepository {
        private final Map<String, NonfinancialPublication> byPeriod =
                new LinkedHashMap<String, NonfinancialPublication>();

        @Override
        public NonfinancialPublication save(NonfinancialPublication publication) {
            byPeriod.put(publication.getPeriod(), publication);
            return publication;
        }

        @Override
        public Optional<NonfinancialPublication> findByPeriod(String period) {
            return Optional.ofNullable(byPeriod.get(period));
        }
    }

    /** 应用层测试自带的巴长型号映射。 */
    private static final class FakeLeaderMapping implements LeaderModelMapping {
        private final Map<String, Set<String>> leadersByModel = new LinkedHashMap<String, Set<String>>();

        void register(String cellModel, String leaderEmployeeNo) {
            leadersByModel.computeIfAbsent(cellModel, k -> new LinkedHashSet<String>())
                    .add(leaderEmployeeNo);
        }

        @Override
        public Set<String> leadersOf(String cellModel) {
            Set<String> leaders = leadersByModel.get(cellModel);
            return leaders == null ? Collections.<String>emptySet()
                    : new LinkedHashSet<String>(leaders);
        }

        @Override
        public Set<String> modelsOf(String leaderEmployeeNo) {
            Set<String> models = new LinkedHashSet<String>();
            for (Map.Entry<String, Set<String>> entry : leadersByModel.entrySet()) {
                if (entry.getValue().contains(leaderEmployeeNo)) {
                    models.add(entry.getKey());
                }
            }
            return models;
        }
    }

    /** 应用层测试自带的权限服务：仅行使基地视角可见范围，其余动作一律拒绝。 */
    private static final class FakePermissionService implements PermissionService {
        private final Map<String, Set<String>> basesByUser =
                new LinkedHashMap<String, Set<String>>();

        void grantBases(String userId, String... baseCodes) {
            basesByUser.put(userId, new LinkedHashSet<String>(Arrays.asList(baseCodes)));
        }

        @Override
        public Set<String> viewableScopes(String userId, AnalysisPerspective perspective) {
            Set<String> bases = basesByUser.get(userId);
            return bases == null ? Collections.<String>emptySet()
                    : new LinkedHashSet<String>(bases);
        }

        @Override
        public UserScope save(UserScope scope) {
            return scope;
        }

        @Override
        public List<UserScope> list(String userId, AnalysisPerspective perspective) {
            return Collections.emptyList();
        }

        @Override
        public boolean canViewScope(String userId, AnalysisPerspective perspective, String scopeCode) {
            return false;
        }

        @Override
        public boolean canViewDetail(String userId, AnalysisPerspective perspective, String scopeCode) {
            return false;
        }

        @Override
        public boolean canExport(String userId, AnalysisPerspective perspective, String scopeCode) {
            return false;
        }

        @Override
        public boolean canEditConfig(String userId, AnalysisPerspective perspective, String scopeCode) {
            return false;
        }

        @Override
        public boolean canViewDraft(String userId, AnalysisPerspective perspective) {
            return false;
        }

        @Override
        public boolean canViewResult(String userId, AnalysisPerspective perspective,
                                     CalculationResultStatus status) {
            return false;
        }

        @Override
        public boolean canPublishAi(String userId, AnalysisPerspective perspective) {
            return false;
        }

        @Override
        public Set<String> detailScopes(String userId, AnalysisPerspective perspective) {
            return Collections.emptySet();
        }

        @Override
        public Set<String> exportScopes(String userId, AnalysisPerspective perspective) {
            return Collections.emptySet();
        }
    }
}
