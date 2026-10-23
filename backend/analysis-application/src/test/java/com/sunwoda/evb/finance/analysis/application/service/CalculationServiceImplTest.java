package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculationServiceImplTest {
    @Test
    void calculatesProfitLinesByPerspectiveAndScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-003", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(100), decimal(1000), decimal(700),
                        decimal(20), decimal(50), decimal(10), decimal(5), decimal(0), decimal(2)));

        CalculationResult result = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository()).calculate("B-003", "finance-001");

        assertEquals(1, result.getResults().size());
        assertEquals(decimal(300), result.getResults().get(0).getLines().get("GROSS_PROFIT"));
        assertEquals(decimal(217), result.getResults().get(0).getLines().get("NET_PROFIT"));
        assertEquals(BatchStatus.CALCULATED, repository.findByBatchNo("B-003").get().getStatus());
    }

    @Test
    void calculatesBudgetAndGapLinesTogether() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-004", "2026-09", AnalysisPerspective.PRODUCT_LINE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(new PnlFact(
                "产品线A", decimal(110), decimal(1100), decimal(760),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                decimal(100), decimal(1000), decimal(700),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO));

        CalculationResult result = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository()).calculate("B-004", "finance-001");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        assertEquals(decimal(1000), lines.get("BUDGET_REVENUE"));
        assertEquals(decimal(300), lines.get("BUDGET_GROSS_PROFIT"));
        assertEquals(decimal(100), lines.get("GAP_REVENUE"));
        assertEquals(decimal(40), lines.get("GAP_GROSS_PROFIT"));
    }

    @Test
    void confirmsThenPublishesResultAndFreezesBatch() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-005", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository());

        service.calculate("B-005", "finance-001");
        CalculationResult confirmed = service.confirm("B-005", "finance-001");
        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals("finance-001", confirmed.getConfirmedBy());
        assertEquals(BatchStatus.CONFIRMED, repository.findByBatchNo("B-005").get().getStatus());

        CalculationResult published = service.publish("B-005", "finance-002");
        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals("finance-002", published.getPublishedBy());
        assertEquals(BatchStatus.PUBLISHED, repository.findByBatchNo("B-005").get().getStatus());
    }

    @Test
    void sameAuthorizedHeadquartersFinanceUserMayConfirmAndPublishWithinScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-005-SAME-OPERATOR", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-HQ-SAME", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);

        service.calculate("B-005-SAME-OPERATOR", "U-HQ-SAME");
        CalculationResult confirmed = service.confirm("B-005-SAME-OPERATOR", "U-HQ-SAME");
        CalculationResult published = service.publish("B-005-SAME-OPERATOR", "U-HQ-SAME");

        assertEquals("U-HQ-SAME", confirmed.getConfirmedBy());
        assertEquals("U-HQ-SAME", published.getPublishedBy());
        assertEquals(BatchStatus.PUBLISHED,
                repository.findByBatchNo("B-005-SAME-OPERATOR").get().getStatus());
    }

    @Test
    void withdrawsPublishedResultWithTraceAndPreservesAmounts() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-WD-OK", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-HQ-WD", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);

        service.calculate("B-WD-OK", "U-HQ-WD");
        service.confirm("B-WD-OK", "U-HQ-WD");
        CalculationResult published = service.publish("B-WD-OK", "U-HQ-WD");
        BigDecimal revenueBefore = published.getResults().get(0).getLines().get("REVENUE");

        CalculationResult withdrawn = service.withdraw("B-WD-OK", "数据口径错误",
                Arrays.asList("finance-lead@example.com", "base-manager"), "U-HQ-WD");

        assertEquals(CalculationResultStatus.WITHDRAWN, withdrawn.getStatus());
        assertEquals("U-HQ-WD", withdrawn.getWithdrawnBy());
        assertEquals("数据口径错误", withdrawn.getWithdrawReason());
        assertEquals(Arrays.asList("finance-lead@example.com", "base-manager"),
                withdrawn.getNotificationTargets());
        // 撤回只改状态与撤回痕迹，金额与指标保持不变。
        assertEquals(revenueBefore, withdrawn.getResults().get(0).getLines().get("REVENUE"));
        // 批次状态不因撤回而变化，仍为已发布。
        assertEquals(BatchStatus.PUBLISHED, repository.findByBatchNo("B-WD-OK").get().getStatus());
    }

    @Test
    void withdrawRejectsResultNotYetPublished() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-WD-NOT-PUB", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-HQ-WD", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);

        service.calculate("B-WD-NOT-PUB", "U-HQ-WD");
        service.confirm("B-WD-NOT-PUB", "U-HQ-WD");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.withdraw("B-WD-NOT-PUB", "数据口径错误",
                        Arrays.asList("finance-lead@example.com"), "U-HQ-WD"));

        assertEquals("只有已发布结果可以撤回: CONFIRMED", error.getMessage());
    }

    @Test
    void withdrawRejectsBlankReason() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-WD-BLANK", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-HQ-WD", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);

        service.calculate("B-WD-BLANK", "U-HQ-WD");
        service.confirm("B-WD-BLANK", "U-HQ-WD");
        service.publish("B-WD-BLANK", "U-HQ-WD");

        assertThrows(IllegalArgumentException.class,
                () -> service.withdraw("B-WD-BLANK", "  ",
                        Arrays.asList("finance-lead@example.com"), "U-HQ-WD"));
    }

    @Test
    void samplePvmResidualDoesNotBlockPnlPublication() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-005-DIFF", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(7), decimal(2), decimal(5),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        decimal(3), decimal(1), decimal(4), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, new FakeUserScopeRepository());

        service.calculate("B-005-DIFF", "finance-001");
        CalculationResult confirmed = service.confirm("B-005-DIFF", "finance-001");

        assertEquals(CalculationResultStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(BatchStatus.CONFIRMED, repository.findByBatchNo("B-005-DIFF").get().getStatus());
        CalculationResult published = service.publish("B-005-DIFF", "finance-002");
        assertEquals(CalculationResultStatus.PUBLISHED, published.getStatus());
        assertEquals(BatchStatus.PUBLISHED, repository.findByBatchNo("B-005-DIFF").get().getStatus());
    }

    @Test
    void filtersResultByUserScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-006", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new PnlFact("宜昌", decimal(2), decimal(20), decimal(12),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U001", "BASE_FINANCE", AnalysisPerspective.BASE,
                "南昌", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);
        service.calculate("B-006", "finance-001");

        CalculationResult filtered = service.getForUser("B-006", "U001");

        assertEquals(1, filtered.getResults().size());
        assertEquals("南昌", filtered.getResults().get(0).getScopeCode());
    }

    @Test
    void marksBatchFailedWhenFactLoadingFails() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-007", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> { throw new IllegalStateException("事实读取失败"); };
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider,
                new FakeUserScopeRepository());

        try {
            service.calculate("B-007", "finance-001");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertEquals(BatchStatus.FAILED, repository.findByBatchNo("B-007").get().getStatus());
    }

    @Test
    void calculateWithoutAuthenticatedOperatorFailsClosed() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-008", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        CalculationService service = new CalculationServiceImpl(repository,
                batch -> java.util.Collections.emptyList(), new FakeUserScopeRepository());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.calculate("B-008"));

        assertEquals("计算必须提供认证操作者", error.getMessage());
        assertEquals(BatchStatus.READY_FOR_CALCULATION,
                repository.findByBatchNo("B-008").get().getStatus());
    }

    @Test
    void calculationRejectsBatchContainingScopeOutsideHeadquartersFinanceGrant() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-009", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("NANCHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new PnlFact("YICHANG", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-HQ-SCOPE", "HQ_FINANCE", AnalysisPerspective.BASE,
                "NANCHANG", true, true, true, true));
        CalculationService service = new CalculationServiceImpl(repository, provider, scopes);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.calculate("B-009", "U-HQ-SCOPE"));

        assertEquals("当前用户无批次涉及范围的计算权限", error.getMessage());
        assertEquals(BatchStatus.READY_FOR_CALCULATION,
                repository.findByBatchNo("B-009").get().getStatus());
    }

    @Test
    void accumulatesYearToDateUpToTargetMonthOnly() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-01", "2026-01", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        repository.save(new AnalysisBatch(2L, "B-02", "2026-02", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        repository.save(new AnalysisBatch(3L, "B-03", "2026-03", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        // 目标月之后的批次不得计入累计。
        repository.save(new AnalysisBatch(4L, "B-04", "2026-04", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> {
            if ("B-01".equals(batch.getBatchNo())) {
                return Arrays.asList(new PnlFact("NANCHANG", decimal(10), decimal(100), decimal(70),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
            }
            if ("B-02".equals(batch.getBatchNo())) {
                return Arrays.asList(new PnlFact("NANCHANG", decimal(20), decimal(200), decimal(140),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
            }
            if ("B-03".equals(batch.getBatchNo())) {
                return Arrays.asList(new PnlFact("NANCHANG", decimal(30), decimal(300), decimal(210),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
            }
            return Arrays.asList(new PnlFact("NANCHANG", decimal(40), decimal(400), decimal(280),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO));
        };
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider,
                new FakeUserScopeRepository());

        CalculationResult cumulative = service.getCumulativeForUser("B-03", "finance-001");

        assertEquals(1, cumulative.getResults().size());
        Map<String, BigDecimal> lines = cumulative.getResults().get(0).getLines();
        assertEquals(decimal(60), lines.get("VOLUME"));
        assertEquals(decimal(600), lines.get("REVENUE"));
        assertEquals(decimal(420), lines.get("SALES_COST"));
        assertEquals(decimal(180), lines.get("GROSS_PROFIT"));
    }

    @Test
    void cumulativeFiltersByUserViewableScope() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-01", "2026-01", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        repository.save(new AnalysisBatch(2L, "B-02", "2026-02", AnalysisPerspective.BASE,
                BatchStatus.CALCULATED, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(
                new PnlFact("南昌", decimal(1), decimal(10), decimal(7),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new PnlFact("宜昌", decimal(2), decimal(20), decimal(12),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        FakeUserScopeRepository scopes = new FakeUserScopeRepository();
        scopes.save(new UserScope("U-YTD-001", "BASE_FINANCE", AnalysisPerspective.BASE,
                "南昌", true, true, true, true));
        CalculationServiceImpl service = new CalculationServiceImpl(repository, provider, scopes);

        CalculationResult cumulative = service.getCumulativeForUser("B-02", "U-YTD-001");

        assertEquals(1, cumulative.getResults().size());
        assertEquals("南昌", cumulative.getResults().get(0).getScopeCode());
        assertEquals(decimal(20), cumulative.getResults().get(0).getLines().get("REVENUE"));
    }

    @Test
    void basePerspectiveDeductsOperatingExpenseAndAddsOtherIncome() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-BASE-EXP", "2026-09", AnalysisPerspective.BASE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(factExpenses("NANCHANG",
                decimal(1000), decimal(700), decimal(10), decimal(5), decimal(2),
                decimal(30), decimal(0), decimal(0)));

        CalculationResult result = new CalculationServiceImpl(repository, provider,
                new FakeUserScopeRepository()).calculate("B-BASE-EXP", "finance-001");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        // projectProfit = (1000-700) - 0 - 0 = 300；net = 300 - 10(研发) - 30(运营) - 5(减值) + 2(其他收益) = 257
        assertEquals(decimal(257), lines.get("NET_PROFIT"));
        assertEquals(decimal(30), lines.get("OPERATING_EXPENSE"));
    }

    @Test
    void productLinePerspectiveIgnoresOtherIncomeAndBaseOperatingExpense() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-PL-EXP", "2026-09", AnalysisPerspective.PRODUCT_LINE,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(factExpenses("产品线A",
                decimal(1000), decimal(700), decimal(10), decimal(5), decimal(2),
                decimal(30), decimal(0), decimal(0)));

        CalculationResult result = new CalculationServiceImpl(repository, provider,
                new FakeUserScopeRepository()).calculate("B-PL-EXP", "finance-001");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        // 产品线利润 = 项目利润 - 研发 - 减值，不取其他收益、不扣运营费用：300 - 10 - 5 = 285
        assertEquals(decimal(285), lines.get("NET_PROFIT"));
    }

    @Test
    void businessUnitPerspectiveDeductsSalesAndAdminExpenseWithoutOtherIncome() {
        FakeBatchRepository repository = new FakeBatchRepository();
        repository.save(new AnalysisBatch(1L, "B-BU-EXP", "2026-09", AnalysisPerspective.BUSINESS_UNIT,
                BatchStatus.READY_FOR_CALCULATION, "tester", LocalDateTime.now()));
        PnlFactProvider provider = batch -> Arrays.asList(factExpenses("事业部A",
                decimal(1000), decimal(700), decimal(10), decimal(5), decimal(2),
                decimal(30), decimal(40), decimal(20)));

        CalculationResult result = new CalculationServiceImpl(repository, provider,
                new FakeUserScopeRepository()).calculate("B-BU-EXP", "finance-001");

        Map<String, BigDecimal> lines = result.getResults().get(0).getLines();
        // 事业部利润 = 项目利润 - 研发 - 销售 - 管理 - 减值，不取其他收益、不扣运营费用：300 - 10 - 40 - 20 - 5 = 225
        assertEquals(decimal(225), lines.get("NET_PROFIT"));
        assertEquals(decimal(40), lines.get("SALES_EXPENSE"));
        assertEquals(decimal(20), lines.get("ADMIN_EXPENSE"));
    }

    /** 构造带三视角费用的最小事实：固定 volume/成本结构，仅暴露会影响净利的字段。 */
    private static PnlFact factExpenses(String scope, BigDecimal revenue, BigDecimal salesCost,
                                        BigDecimal rdExpense, BigDecimal assetImpairment, BigDecimal otherIncome,
                                        BigDecimal operatingExpense, BigDecimal salesExpense, BigDecimal adminExpense) {
        BigDecimal z = BigDecimal.ZERO;
        return new PnlFact(scope, null, null, null,
                decimal(100), revenue, salesCost, z, z,
                rdExpense, assetImpairment, z, otherIncome,
                z, z, z, z, z,
                z, z, z,
                z, z, z, z, z, z, z, z, z, z, z,
                null, null, null, null,
                operatingExpense, salesExpense, adminExpense,
                z, z, z);
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }

    private static class FakeBatchRepository implements AnalysisBatchRepository {
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

        @Override
        public List<AnalysisBatch> findByPerspectiveAndPeriodRange(AnalysisPerspective perspective,
                                                                   String fromPeriod, String toPeriod) {
            List<AnalysisBatch> matched = new ArrayList<AnalysisBatch>();
            for (AnalysisBatch batch : data.values()) {
                if (batch.getPerspective() == perspective
                        && batch.getPeriod().compareTo(fromPeriod) >= 0
                        && batch.getPeriod().compareTo(toPeriod) <= 0) {
                    matched.add(batch);
                }
            }
            matched.sort(Comparator.comparing(AnalysisBatch::getPeriod));
            return matched;
        }
    }

    private static class FakeUserScopeRepository implements UserScopeRepository {
        private final java.util.List<UserScope> data = new java.util.ArrayList<UserScope>();
        @Override public UserScope save(UserScope scope) { data.add(scope); return scope; }
        @Override public java.util.List<UserScope> findByUserAndPerspective(String userId,
                                                                              AnalysisPerspective perspective) {
            java.util.List<UserScope> result = new java.util.ArrayList<UserScope>();
            for (UserScope scope : data) {
                if (scope.isEnabled() && scope.getUserId().equals(userId)
                        && scope.getPerspective() == perspective) result.add(scope);
            }
            // 本次修改：为既有生命周期测试补充显式总部财经授权，不改变生产默认拒绝策略。
            if (result.isEmpty() && userId != null && userId.startsWith("finance-")) {
                result.add(new UserScope(userId, "HQ_FINANCE", perspective,
                        "ALL", true, true, true, true));
            }
            return result;
        }
    }
}
