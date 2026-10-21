package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationEventDeliveryStatus;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationLifecycleEvent;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ManualCalculationRevision;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationLifecycleEventRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.ManualCalculationRevisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 历史计算版本人工修订应用服务。
 *
 * <p>本次修改：提供授权后追加新 calculation_version 的最小入口；旧版本只通过仓储契约
 * 转为已替代指针，修订关系和 MANUAL_REVISED 生命周期事件独立落库，禁止复用旧版本主键。</p>
 *
 * <p>本次修改：仅在 db profile 下作为显式应用服务 Bean；人工修订需要生产持久化版本、
 * 修订关系和生命周期事件，local 未接入这些端口时不创建可误用的服务。</p>
 */
@Service
// 本次修改：收敛到 db Profile，local 缺少持久化依赖时按失败关闭处理。
@Profile("db")
public class ManualCalculationRevisionService {
    private static final String EVENT_TYPE = "MANUAL_REVISED";
    private static final String AGGREGATE_TYPE = "CALCULATION_VERSION";
    // 本次修改：固定业务时间语义为中国时区，避免机器默认时区造成修订审计时间漂移。
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final CalculationVersionRepository versionRepository;
    private final ManualCalculationRevisionRepository revisionRepository;
    private final CalculationLifecycleEventRepository lifecycleEventRepository;
    private final ManualRevisionAuthorizer authorizer;
    private final Clock clock;

    @Autowired
    public ManualCalculationRevisionService(CalculationVersionRepository versionRepository,
                                            ManualCalculationRevisionRepository revisionRepository,
                                            CalculationLifecycleEventRepository lifecycleEventRepository,
                                            ManualRevisionAuthorizer authorizer) {
        this(versionRepository, revisionRepository, lifecycleEventRepository, authorizer,
                Clock.system(BUSINESS_ZONE));
    }

    public ManualCalculationRevisionService(CalculationVersionRepository versionRepository,
                                            ManualCalculationRevisionRepository revisionRepository,
                                            CalculationLifecycleEventRepository lifecycleEventRepository,
                                            ManualRevisionAuthorizer authorizer,
                                            Clock clock) {
        if (versionRepository == null || revisionRepository == null
                || lifecycleEventRepository == null || authorizer == null || clock == null) {
            throw new IllegalArgumentException("人工修订服务依赖不能为空");
        }
        this.versionRepository = versionRepository;
        this.revisionRepository = revisionRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.authorizer = authorizer;
        this.clock = clock;
    }

    /**
     * 授权后从指定历史版本追加一个新的计算版本。
     *
     * <p>本次修改：先读取并授权，再在同一事务中追加目标版本、修订关系和生命周期事件，
     * 从调用顺序上阻断未授权写入和旧版本覆盖。</p>
     */
    @Transactional
    public synchronized CalculationVersion revise(String sourceVersionId,
                                                   String reviser,
                                                   String revisionReason) {
        String sourceId = required(sourceVersionId, "sourceVersionId");
        String operator = required(reviser, "reviser");
        String reason = required(revisionReason, "revisionReason");
        CalculationVersion source = versionRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("来源计算版本不存在: " + sourceId));

        authorizer.assertAuthorized(operator, source);

        String revisionId = "REV-" + UUID.randomUUID().toString();
        String targetVersionId = "CALC-MR-" + UUID.randomUUID().toString();
        String traceId = "TRACE-MR-" + UUID.randomUUID().toString();
        LocalDateTime revisedAt = LocalDateTime.now(clock);
        String targetVersionNo = "MR-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12);

        CalculationVersion target = new CalculationVersion(
                targetVersionId,
                source.getBatchNo(),
                source.getPerspective(),
                targetVersionNo,
                source.getJobId(),
                source.getSnapshotId(),
                source.getSourceVersionHash(),
                source.getConfigHash(),
                source.getInputFingerprint(),
                CalculationVersionStatus.CALCULATED,
                true,
                source.getCalculationVersionId(),
                traceId,
                operator,
                revisedAt,
                null,
                null,
                null,
                null);
        ManualCalculationRevision revision = new ManualCalculationRevision(
                revisionId, source.getCalculationVersionId(), target.getCalculationVersionId(),
                operator, reason, revisedAt, traceId);

        revisionRepository.append(source, target, revision);
        lifecycleEventRepository.save(new CalculationLifecycleEvent(
                "EVENT-" + revisionId,
                AGGREGATE_TYPE,
                target.getCalculationVersionId(),
                EVENT_TYPE,
                target.getCalculationVersionId(),
                traceId,
                toPayload(revision),
                CalculationEventDeliveryStatus.PENDING,
                0,
                revisedAt,
                null,
                revisedAt,
                null));
        return target;
    }

    private String toPayload(ManualCalculationRevision revision) {
        return "{"
                + "\"revisionId\":\"" + jsonEscape(revision.getRevisionId()) + "\","
                + "\"sourceVersionId\":\"" + jsonEscape(revision.getSourceCalculationVersionId()) + "\","
                + "\"targetVersionId\":\"" + jsonEscape(revision.getTargetCalculationVersionId()) + "\","
                + "\"revisedBy\":\"" + jsonEscape(revision.getRevisedBy()) + "\","
                + "\"revisionReason\":\"" + jsonEscape(revision.getRevisionReason()) + "\","
                + "\"revisedAt\":\"" + jsonEscape(revision.getRevisedAt().toString()) + "\""
                + "}";
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '"') escaped.append('\\');
            if (ch == '\n') escaped.append('\\').append('n');
            else if (ch == '\r') escaped.append('\\').append('r');
            else if (ch == '\t') escaped.append('\\').append('t');
            else escaped.append(ch);
        }
        return escaped.toString();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
