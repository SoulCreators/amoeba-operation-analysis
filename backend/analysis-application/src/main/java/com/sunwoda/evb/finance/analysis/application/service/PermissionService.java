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
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canConfirm(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canPublish(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canManualRevise(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：默认拒绝，避免旧实现因接口扩展而获得任何新的写权限。
    default boolean canCompensate(String userId, AnalysisPerspective perspective) {
        return false;
    }
    // 本次新增：为调整、补偿等尚未落地的应用服务保留统一动作判定接口。
    // 本次新增：统一动作接口默认拒绝，防止旧实现被动继承新动作。
    default boolean canPerform(String userId, AnalysisPerspective perspective, PermissionAction action) {
        return false;
    }
    Set<String> viewableScopes(String userId, AnalysisPerspective perspective);
    Set<String> detailScopes(String userId, AnalysisPerspective perspective);
    Set<String> exportScopes(String userId, AnalysisPerspective perspective);
}
