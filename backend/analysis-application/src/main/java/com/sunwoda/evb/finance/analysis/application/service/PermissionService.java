package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.model.PermissionAction;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.Set;
import java.util.List;

public interface PermissionService {
    UserScope save(UserScope scope);
    List<UserScope> list(String userId, AnalysisPerspective perspective);
    boolean canViewScope(String userId, AnalysisPerspective perspective, String scopeCode);
    boolean canViewDetail(String userId, AnalysisPerspective perspective, String scopeCode);
    boolean canExport(String userId, AnalysisPerspective perspective, String scopeCode);
    boolean canEditConfig(String userId, AnalysisPerspective perspective, String scopeCode);
    boolean canViewDraft(String userId, AnalysisPerspective perspective);
    // 本次新增：向 API HTTP 只读门禁暴露已启用 ADMIN 判定，默认拒绝以兼容旧实现。
    default boolean isEnabledAdmin(String userId) {
        return false;
    }
    // 本次新增：提供历史结果读取判定，避免调用方通过发布权限反推历史权限。
    // 本次新增：默认拒绝，保证旧的测试替身和其他实现不会意外获得历史读取权。
    default boolean canViewHistory(String userId, AnalysisPerspective perspective) {
        return false;
    }
    boolean canViewResult(String userId, AnalysisPerspective perspective,
                          CalculationResultStatus status);
    boolean canPublishAi(String userId, AnalysisPerspective perspective);
    // 本次新增：总部财经生命周期动作的显式权限入口；ADMIN 不从读取权限继承这些写权限。
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canCalculate(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：计算动作的范围级便捷入口，要求覆盖批次实际涉及的全部 scope。
    default boolean canCalculate(String userId, AnalysisPerspective perspective, Set<String> scopeCodes) {
        return canPerform(userId, perspective, PermissionAction.CALCULATE, scopeCodes);
    }
    // 本次新增：计算动作的单 scope 便捷入口。
    default boolean canCalculate(String userId, AnalysisPerspective perspective, String scopeCode) {
        return canPerform(userId, perspective, PermissionAction.CALCULATE, scopeCode);
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canConfirm(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：确认动作的范围级便捷入口，避免只按视角判定。
    default boolean canConfirm(String userId, AnalysisPerspective perspective, Set<String> scopeCodes) {
        return canPerform(userId, perspective, PermissionAction.CONFIRM, scopeCodes);
    }
    // 本次新增：确认动作的单 scope 便捷入口。
    default boolean canConfirm(String userId, AnalysisPerspective perspective, String scopeCode) {
        return canPerform(userId, perspective, PermissionAction.CONFIRM, scopeCode);
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canPublish(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：发布动作的范围级便捷入口，避免只按视角判定。
    default boolean canPublish(String userId, AnalysisPerspective perspective, Set<String> scopeCodes) {
        return canPerform(userId, perspective, PermissionAction.PUBLISH, scopeCodes);
    }
    // 本次新增：发布动作的单 scope 便捷入口。
    default boolean canPublish(String userId, AnalysisPerspective perspective, String scopeCode) {
        return canPerform(userId, perspective, PermissionAction.PUBLISH, scopeCode);
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canManualRevise(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：人工修订动作的范围级便捷入口。
    default boolean canManualRevise(String userId, AnalysisPerspective perspective, Set<String> scopeCodes) {
        return canPerform(userId, perspective, PermissionAction.MANUAL_REVISION, scopeCodes);
    }
    // 本次新增：人工修订动作的单 scope 便捷入口。
    default boolean canManualRevise(String userId, AnalysisPerspective perspective, String scopeCode) {
        return canPerform(userId, perspective, PermissionAction.MANUAL_REVISION, scopeCode);
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canCompensate(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：人工补偿动作的范围级便捷入口。
    default boolean canCompensate(String userId, AnalysisPerspective perspective, Set<String> scopeCodes) {
        return canPerform(userId, perspective, PermissionAction.COMPENSATE, scopeCodes);
    }
    // 本次新增：人工补偿动作的单 scope 便捷入口。
    default boolean canCompensate(String userId, AnalysisPerspective perspective, String scopeCode) {
        return canPerform(userId, perspective, PermissionAction.COMPENSATE, scopeCode);
    }
    // 本次新增：为调整、补偿等尚未落地的应用服务保留统一动作判定接口。
    // 本次新增：统一动作接口默认拒绝，防止旧实现被动继承新动作。
    default boolean canPerform(String userId, AnalysisPerspective perspective, PermissionAction action) {
        return false;
    }
    // 本次新增：把生命周期动作绑定到实际业务范围，禁止用视角级任意授权覆盖未授权 scope。
    default boolean canPerform(String userId, AnalysisPerspective perspective,
                               PermissionAction action, String scopeCode) {
        return false;
    }
    // 本次新增：批量动作必须覆盖其涉及的全部 scope；空集合沿用视角级角色判定。
    default boolean canPerform(String userId, AnalysisPerspective perspective,
                               PermissionAction action, Set<String> scopeCodes) {
        if (scopeCodes == null || scopeCodes.isEmpty()) {
            return canPerform(userId, perspective, action);
        }
        for (String scopeCode : scopeCodes) {
            if (!canPerform(userId, perspective, action, scopeCode)) return false;
        }
        return true;
    }
    Set<String> viewableScopes(String userId, AnalysisPerspective perspective);
    Set<String> detailScopes(String userId, AnalysisPerspective perspective);
    Set<String> exportScopes(String userId, AnalysisPerspective perspective);
}
