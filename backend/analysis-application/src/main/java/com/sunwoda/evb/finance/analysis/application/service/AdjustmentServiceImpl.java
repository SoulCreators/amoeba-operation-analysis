package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.Adjustment;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentDirection;
import com.sunwoda.evb.finance.analysis.domain.model.AdjustmentScenario;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PermissionAction;
import com.sunwoda.evb.finance.analysis.domain.repository.AdjustmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AdjustmentServiceImpl implements AdjustmentService {
    private final AdjustmentRepository repository;
    private final PermissionService permissionService;

    @Autowired
    public AdjustmentServiceImpl(AdjustmentRepository repository, PermissionService permissionService) {
        if (repository == null || permissionService == null) {
            throw new IllegalArgumentException("调整服务依赖不能为空");
        }
        this.repository = repository;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public synchronized Adjustment create(String period, AnalysisPerspective perspective, String scopeCode,
                                          AdjustmentScenario scenario, String metricCode, BigDecimal amount,
                                          AdjustmentDirection direction, String reason, String evidenceFileId,
                                          String expectedVersion, String operator) {
        String normalizedOperator = requireText(operator, "operator");
        Set<String> scopes = Collections.singleton(requireText(scopeCode, "scopeCode"));
        // 本次新增：调整录入是独立写动作，仅当前视角下有授权范围的总部财经可执行。
        if (!permissionService.canPerform(normalizedOperator, perspective, PermissionAction.ADJUSTMENT, scopes)) {
            throw new IllegalStateException("当前用户无调整录入权限");
        }
        Adjustment draft = Adjustment.create(
                "ADJ-" + UUID.randomUUID().toString(), requireText(period, "period"), perspective,
                scopeCode.trim(), scenario, requireText(metricCode, "metricCode"), amount, direction,
                requireText(reason, "reason"), evidenceFileId, expectedVersion,
                normalizedOperator, LocalDateTime.now());
        Adjustment active = draft.activate();
        repository.save(active);
        return active;
    }

    @Override
    public List<Adjustment> list(String period, AnalysisPerspective perspective, String userId) {
        requireText(period, "period");
        String normalizedUser = requireText(userId, "userId");
        List<Adjustment> all = repository.findByPeriod(period);
        Set<String> visible = permissionService.viewableScopes(normalizedUser, perspective);
        List<Adjustment> result = new ArrayList<Adjustment>();
        for (Adjustment adjustment : all) {
            if (adjustment.getPerspective() != perspective) continue;
            if (visible.contains("ALL") || visible.contains(adjustment.getScopeCode())) {
                result.add(adjustment);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public synchronized Adjustment voidAdjustment(String adjustmentId, String reason, String operator) {
        String normalizedOperator = requireText(operator, "operator");
        Adjustment current = repository.findById(requireText(adjustmentId, "adjustmentId"))
                .orElseThrow(() -> new IllegalArgumentException("调整不存在: " + adjustmentId));
        Set<String> scopes = Collections.singleton(current.getScopeCode());
        if (!permissionService.canPerform(normalizedOperator, current.getPerspective(),
                PermissionAction.ADJUSTMENT, scopes)) {
            throw new IllegalStateException("当前用户无调整作废权限");
        }
        Adjustment voided = current.voidAdjustment(normalizedOperator, LocalDateTime.now(),
                requireText(reason, "reason"));
        repository.save(voided);
        return voided;
    }

    @Override
    public List<Adjustment> listActive(String period, AnalysisPerspective perspective) {
        requireText(period, "period");
        if (perspective == null) {
            throw new IllegalArgumentException("perspective 不能为空");
        }
        return repository.findActive(period, perspective);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
