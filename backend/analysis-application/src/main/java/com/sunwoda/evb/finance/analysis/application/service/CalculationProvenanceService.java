package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationProvenance;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 历史上游配置重放（FR-AUD-001）应用服务。
 *
 * <p>按版本号或追踪号还原该结果冻结的输入快照。读取前执行审计只读门禁
 * （复用 {@link AuditEventReadPolicy}，仅启用 ADMIN），不把普通用户的查看/
 * 明细/导出范围推导为审计读取权。只读不写，不触发计算，不改动版本状态。</p>
 */
@Service
@Profile("db")
public final class CalculationProvenanceService {
    private final AuditEventReadPolicy readPolicy;
    private final CalculationVersionRepository versionRepository;
    private final CalculationSnapshotRepository snapshotRepository;

    public CalculationProvenanceService(AuditEventReadPolicy readPolicy,
                                        CalculationVersionRepository versionRepository,
                                        CalculationSnapshotRepository snapshotRepository) {
        if (readPolicy == null) {
            throw new IllegalArgumentException("readPolicy 不能为空");
        }
        if (versionRepository == null) {
            throw new IllegalArgumentException("versionRepository 不能为空");
        }
        if (snapshotRepository == null) {
            throw new IllegalArgumentException("snapshotRepository 不能为空");
        }
        this.readPolicy = readPolicy;
        this.versionRepository = versionRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public CalculationProvenance replayByVersion(String calculationVersionId, String userId) {
        requireAudit(userId);
        CalculationVersion version = versionRepository.findById(required(calculationVersionId, "calculationVersionId"))
                .orElseThrow(() -> new IllegalArgumentException("计算版本不存在: " + calculationVersionId));
        return assemble(version);
    }

    public CalculationProvenance replayByTrace(String traceId, String userId) {
        requireAudit(userId);
        CalculationVersion version = versionRepository.findByTraceId(required(traceId, "traceId"))
                .orElseThrow(() -> new IllegalArgumentException("追踪号不存在: " + traceId));
        return assemble(version);
    }

    private CalculationProvenance assemble(CalculationVersion version) {
        CalculationSnapshot snapshot = snapshotRepository.findById(version.getSnapshotId())
                .orElseThrow(() -> new IllegalStateException("计算版本缺少输入快照: " + version.getSnapshotId()));
        return new CalculationProvenance(version, snapshot);
    }

    private void requireAudit(String userId) {
        if (!readPolicy.canReadAll(userId)) {
            throw new IllegalStateException("当前用户无审计查询权限");
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
