package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.infrastructure.outbox.CalculationLifecycleEventDeliveryClaim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * V032 Outbox 领取、租约恢复和 CAS 确认的 PostgreSQL 契约测试。
 *
 * <p>本次新增测试：验证多个 Worker 不重复领取、租约过期可接管、旧令牌不能覆盖新结果，
 * 并验证 FAILED 与 MANUAL_REQUIRED 的状态边界。</p>
 */
class JdbcCalculationLifecycleEventDeliveryRepositoryPostgresContractTest {
    private static JdbcTemplate jdbc;
    private String eventId;

    @BeforeAll
    static void verifyPostgres() {
        String url = System.getenv("AMOEBA_DB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(),
                "BLOCKED: AMOEBA_DB_URL is not set; V032 Outbox contract was not run");
        DataSource dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("SELECT 1 FROM flow.calculation_event_outbox LIMIT 0");
            jdbc.execute("SELECT 1 FROM flow.calculation_event_outbox_claim LIMIT 0");
        } catch (RuntimeException failure) {
            assumeTrue(false, "BLOCKED: V032 Outbox claim schema is not available: " + failure.getMessage());
        }
    }

    @BeforeEach
    void seedEvent() {
        eventId = "TEST-V032-OUTBOX-" + UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().minusMinutes(1);
        jdbc.update("INSERT INTO flow.calculation_event_outbox "
                        + "(event_id, aggregate_type, aggregate_id, event_type, payload_json, "
                        + "delivery_status, attempt_no, next_attempt_at, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', 0, ?, ?)",
                eventId, "CALCULATION_VERSION", eventId, "TEST_V032", "{\"source\":\"contract\"}",
                java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
    }

    @AfterEach
    void cleanupEvent() {
        if (eventId == null) {
            return;
        }
        jdbc.update("DELETE FROM flow.calculation_event_outbox_claim WHERE event_id = ?", eventId);
        jdbc.update("DELETE FROM flow.calculation_event_outbox WHERE event_id = ?", eventId);
    }

    @Test
    void claimIsAtomicAndSuccessfulConfirmationReleasesClaim() {
        JdbcCalculationLifecycleEventDeliveryRepository repository = repository();
        LocalDateTime now = LocalDateTime.now();

        List<CalculationLifecycleEventDeliveryClaim> first = repository.claimReadyForDelivery(
                now, 10, Duration.ofMinutes(5));
        List<CalculationLifecycleEventDeliveryClaim> second = repository.claimReadyForDelivery(
                now, 10, Duration.ofMinutes(5));

        assertEquals(1, first.size());
        assertTrue(second.isEmpty(), "活动租约内第二个 Worker 不得重复领取");
        CalculationLifecycleEventDeliveryClaim claim = first.get(0);
        assertEquals(CalculationEventDeliveryStatus.IN_PROGRESS, claim.getEvent().getDeliveryStatus());
        assertEquals(1, claim.getEvent().getAttemptNo());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.calculation_event_outbox_claim WHERE event_id = ?",
                Integer.class, eventId).intValue());

        assertFalse(repository.markDelivered(eventId, "stale-token", now));
        assertTrue(repository.markDelivered(eventId, claim.getClaimToken(), now));
        assertEquals("DELIVERED", jdbc.queryForObject(
                "SELECT delivery_status FROM flow.calculation_event_outbox WHERE event_id = ?",
                String.class, eventId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flow.calculation_event_outbox_claim WHERE event_id = ?",
                Integer.class, eventId).intValue());
    }

    @Test
    void failedDeliveryCanRetryAndCanBeMovedToManualRequired() {
        JdbcCalculationLifecycleEventDeliveryRepository repository = repository();
        LocalDateTime now = LocalDateTime.now();
        CalculationLifecycleEventDeliveryClaim first = repository.claimReadyForDelivery(
                now, 1, Duration.ofMinutes(5)).get(0);

        LocalDateTime retryAt = now.plusMinutes(1);
        assertTrue(repository.markFailed(eventId, first.getClaimToken(), CalculationEventDeliveryStatus.FAILED,
                "第一次投递失败", retryAt));
        assertTrue(repository.claimReadyForDelivery(now, 1, Duration.ofMinutes(5)).isEmpty());

        CalculationLifecycleEventDeliveryClaim retry = repository.claimReadyForDelivery(
                retryAt.plusSeconds(1), 1, Duration.ofMinutes(5)).get(0);
        assertEquals(2, retry.getEvent().getAttemptNo());
        assertTrue(repository.markFailed(eventId, retry.getClaimToken(),
                CalculationEventDeliveryStatus.MANUAL_REQUIRED, "超过自动重试上限", retryAt));
        assertTrue(repository.claimReadyForDelivery(retryAt.plusDays(1), 1, Duration.ofMinutes(5)).isEmpty());
        assertEquals("MANUAL_REQUIRED", jdbc.queryForObject(
                "SELECT delivery_status FROM flow.calculation_event_outbox WHERE event_id = ?",
                String.class, eventId));
    }

    @Test
    void expiredLeaseCanBeTakenOverAndOldTokenCannotConfirm() {
        JdbcCalculationLifecycleEventDeliveryRepository firstRepository = repository();
        JdbcCalculationLifecycleEventDeliveryRepository secondRepository = repository();
        LocalDateTime now = LocalDateTime.now();
        CalculationLifecycleEventDeliveryClaim first = firstRepository.claimReadyForDelivery(
                now, 1, Duration.ofMinutes(5)).get(0);

        CalculationLifecycleEventDeliveryClaim takeover = secondRepository.claimReadyForDelivery(
                now.plusMinutes(6), 1, Duration.ofMinutes(5)).get(0);
        assertNotEquals(first.getClaimToken(), takeover.getClaimToken());
        assertEquals(2, takeover.getEvent().getAttemptNo());
        assertFalse(firstRepository.markDelivered(eventId, first.getClaimToken(), now.plusMinutes(6)));
        assertTrue(secondRepository.markDelivered(eventId, takeover.getClaimToken(), now.plusMinutes(6)));
    }

    private JdbcCalculationLifecycleEventDeliveryRepository repository() {
        String url = System.getenv("AMOEBA_DB_URL");
        DataSource dataSource = new DriverManagerDataSource(url, environment("AMOEBA_DB_USERNAME"),
                environment("AMOEBA_DB_PASSWORD"));
        return new JdbcCalculationLifecycleEventDeliveryRepository(dataSource);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
