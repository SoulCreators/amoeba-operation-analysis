package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.CalculationVersionMaterializationCommand;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationSnapshot;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationSnapshotRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计算输入快照与计算版本头物化应用服务。
 *
 * <p>本次新增：只负责将调用方显式提供的快照和版本头以固定顺序写入，
 * 不接入计算编排、确认发布、Outbox、审计、权限或数据库迁移。</p>
 */
@Service
// 本次修改：仅在 db Profile 注册物化服务；local Profile 没有生产快照仓储，避免启动时误要求 JDBC Bean。
@Profile("db")
public class CalculationVersionMaterializationService {
    private final CalculationSnapshotRepository snapshotRepository;
    private final CalculationVersionRepository versionRepository;

    @Autowired
    public CalculationVersionMaterializationService(CalculationSnapshotRepository snapshotRepository,
                                                    CalculationVersionRepository versionRepository) {
        if (snapshotRepository == null) {
            throw new IllegalArgumentException("snapshotRepository 不能为空");
        }
        if (versionRepository == null) {
            throw new IllegalArgumentException("versionRepository 不能为空");
        }
        this.snapshotRepository = snapshotRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * 以同一事务先保存快照，再保存版本头。
     *
     * <p>本次新增：仅校验快照与版本头的显式关联；任一仓储异常不捕获、不转换，
     * 由事务边界向上抛出并触发失败关闭。</p>
     */
    @Transactional
    public CalculationVersion materialize(CalculationVersionMaterializationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("materialization command 不能为空");
        }
        CalculationSnapshot snapshot = command.getSnapshot();
        CalculationVersion version = command.getVersion();
        if (!snapshot.getSnapshotId().equals(version.getSnapshotId())) {
            throw new IllegalArgumentException("计算版本与输入快照的 snapshotId 不一致");
        }

        snapshotRepository.save(snapshot);
        return versionRepository.save(version);
    }
}
