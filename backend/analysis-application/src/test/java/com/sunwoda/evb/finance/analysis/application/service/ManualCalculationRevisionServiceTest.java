package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationLifecycleEventRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ManualCalculationRevisionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史版本人工修订应用服务独立契约测试。
 *
 * <p>本次修改：用内存端口验证授权、追加新版本、修订审计关系和生命周期事件的边界，
 * 不依赖 Spring、数据库、V026 文件或现有 Worker。</p>
 */
class ManualCalculationRevisionServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 30);

    @Test
    void authorizedRevisionCreatesNewVersionAndIndependentAuditAndLifecycleEvent() {
        CalculationVersion source = sourceVersion();
        InMemoryVersionRepository versions = new InMemoryVersionRepository(source);
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        InMemoryLifecycleRepository events = new InMemoryLifecycleRepository();
        RecordingAuthorizer authorizer = new RecordingAuthorizer(true);
        ManualCalculationRevisionService service = service(versions, revisions, events, authorizer);

        CalculationVersion target = service.revise(source.getCalculationVersionId(), "reviser-001",
                "补录人工调整后的材料成本");

        assertNotEquals(source.getCalculationVersionId(), target.getCalculationVersionId());
        assertNotEquals(source.getVersionNo(), target.getVersionNo());
        assertEquals(source.getCalculationVersionId(), target.getSupersedesVersionId());
        assertEquals(CalculationVersionStatus.CALCULATED, target.getStatus());
        assertTrue(target.isCurrent());
        assertEquals("reviser-001", target.getCalculatedBy());
        assertEquals(NOW, target.getCalculatedAt());

        assertEquals(1, revisions.saved.size());
        ManualCalculationRevision revision = revisions.saved.get(0);
        assertEquals(source.getCalculationVersionId(), revision.getSourceCalculationVersionId());
        assertEquals(target.getCalculationVersionId(), revision.getTargetCalculationVersionId());
        assertEquals("reviser-001", revision.getRevisedBy());
        assertEquals("补录人工调整后的材料成本", revision.getRevisionReason());
        assertEquals(NOW, revision.getRevisedAt());

        assertEquals(1, events.saved.size());
        CalculationLifecycleEvent event = events.saved.get(0);
        assertEquals("MANUAL_REVISED", event.getEventType());
        assertEquals(target.getCalculationVersionId(), event.getCalculationVersionId());
        assertEquals(CalculationEventDeliveryStatus.PENDING, event.getDeliveryStatus());
        assertTrue(event.getPayloadJson().contains(source.getCalculationVersionId()));
        assertTrue(event.getPayloadJson().contains("补录人工调整后的材料成本"));

        // 本次修改：确认服务未通过可变对象覆盖来源版本，来源版本仍保持原状态和当前指针。
        assertEquals(CalculationVersionStatus.PUBLISHED, source.getStatus());
        assertTrue(source.isCurrent());
        assertEquals(1, authorizer.calls);
    }

    @Test
    void unauthorizedRevisionDoesNotWriteAnyVersionRevisionOrEvent() {
        CalculationVersion source = sourceVersion();
        InMemoryVersionRepository versions = new InMemoryVersionRepository(source);
        InMemoryRevisionRepository revisions = new InMemoryRevisionRepository();
        InMemoryLifecycleRepository events = new InMemoryLifecycleRepository();
        RecordingAuthorizer authorizer = new RecordingAuthorizer(false);
        ManualCalculationRevisionService service = service(versions, revisions, events, authorizer);

        assertThrows(IllegalStateException.class,
                () -> service.revise(source.getCalculationVersionId(), "reviser-002", "没有授权的尝试"));

        assertEquals(0, revisions.saved.size());
        assertEquals(0, events.saved.size());
        assertEquals(1, versions.saved.size());
        assertEquals(1, authorizer.calls);
    }

    @Test
    void blankReasonIsRejectedBeforeSourceLookup() {
        InMemoryVersionRepository versions = new InMemoryVersionRepository(sourceVersion());
        RecordingAuthorizer authorizer = new RecordingAuthorizer(true);
        ManualCalculationRevisionService service = service(versions, new InMemoryRevisionRepository(),
                new InMemoryLifecycleRepository(), authorizer);

        assertThrows(IllegalArgumentException.class,
                () -> service.revise("source-001", "reviser-003", "  "));
        assertEquals(0, authorizer.calls);
    }

    private ManualCalculationRevisionService service(InMemoryVersionRepository versions,
                                                     InMemoryRevisionRepository revisions,
                                                     InMemoryLifecycleRepository events,
                                                     RecordingAuthorizer authorizer) {
        return new ManualCalculationRevisionService(versions, revisions, events, authorizer,
                // 本次修改：测试 Clock 与生产服务统一使用 Asia/Shanghai 业务时区，避免 8 小时时区偏差。
                Clock.fixed(Instant.parse("2026-10-21T02:30:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private CalculationVersion sourceVersion() {
        return new CalculationVersion(
                "source-001", "B-001", AnalysisPerspective.BASE, "V026-1",
                "job-001", "snapshot-001", "source-hash", "config-hash", "input-hash",
                CalculationVersionStatus.PUBLISHED, true, null, "trace-source", "calculator",
                NOW.minusDays(1), "confirmer", NOW.minusHours(20), "publisher", NOW.minusHours(10));
    }

    private static class RecordingAuthorizer implements ManualRevisionAuthorizer {
        private final boolean allowed;
        private int calls;

        private RecordingAuthorizer(boolean allowed) { this.allowed = allowed; }

        @Override
        public void assertAuthorized(String reviser, CalculationVersion sourceVersion) {
            calls++;
            if (!allowed) throw new IllegalStateException("当前用户无历史版本人工修订权限");
        }
    }

    private static class InMemoryVersionRepository implements CalculationVersionRepository {
        private final List<CalculationVersion> saved = new ArrayList<CalculationVersion>();
        private final CalculationVersion source;

        private InMemoryVersionRepository(CalculationVersion source) {
            this.source = source;
            saved.add(source);
        }

        @Override
        public CalculationVersion save(CalculationVersion version) {
            saved.add(version);
            return version;
        }

        @Override
        public Optional<CalculationVersion> findById(String calculationVersionId) {
            if (source.getCalculationVersionId().equals(calculationVersionId)) {
                return Optional.of(source);
            }
            for (CalculationVersion version : saved) {
                if (version.getCalculationVersionId().equals(calculationVersionId)) {
                    return Optional.of(version);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<CalculationVersion> findCurrentByBatchNo(String batchNo,
                                                                  AnalysisPerspective perspective) {
            return Optional.empty();
        }

        @Override
        public List<CalculationVersion> findByBatchNo(String batchNo, AnalysisPerspective perspective) {
            return Collections.unmodifiableList(saved);
        }
    }

    private static class InMemoryRevisionRepository implements ManualCalculationRevisionRepository {
        private final List<ManualCalculationRevision> saved = new ArrayList<ManualCalculationRevision>();

        @Override
        public CalculationVersion append(CalculationVersion sourceVersion,
                                          CalculationVersion targetVersion,
                                          ManualCalculationRevision revision) {
            saved.add(revision);
            return targetVersion;
        }

        @Override
        public Optional<ManualCalculationRevision> findByTargetVersionId(String targetVersionId) {
            for (ManualCalculationRevision revision : saved) {
                if (revision.getTargetCalculationVersionId().equals(targetVersionId)) {
                    return Optional.of(revision);
                }
            }
            return Optional.empty();
        }
    }

    private static class InMemoryLifecycleRepository implements CalculationLifecycleEventRepository {
        private final List<CalculationLifecycleEvent> saved = new ArrayList<CalculationLifecycleEvent>();

        @Override
        public CalculationLifecycleEvent save(CalculationLifecycleEvent event) {
            saved.add(event);
            return event;
        }

        @Override
        public Optional<CalculationLifecycleEvent> findByEventId(String eventId) {
            for (CalculationLifecycleEvent event : saved) {
                if (event.getEventId().equals(eventId)) return Optional.of(event);
            }
            return Optional.empty();
        }

        @Override
        public List<CalculationLifecycleEvent> findReadyForDelivery(
                CalculationEventDeliveryStatus status, LocalDateTime now, int limit) {
            return Collections.unmodifiableList(saved);
        }
    }
}
