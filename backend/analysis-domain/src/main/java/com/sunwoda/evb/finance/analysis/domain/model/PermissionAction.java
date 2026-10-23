package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * Application-level capabilities represented by a user scope grant.
 *
 * <p>The enum is deliberately small: it describes the permissions already
 * represented by {@link UserScope} and does not infer any new business role.
 */
public enum PermissionAction {
    VIEW_SCOPE,
    VIEW_DETAIL,
    EXPORT,
    EDIT_CONFIG,
    // 本次新增：把总部财经最小切片要求的草稿/历史读取和生命周期写动作显式建模。
    VIEW_DRAFT,
    VIEW_HISTORY,
    CALCULATE,
    CONFIRM,
    PUBLISH,
    MANUAL_REVISION,
    COMPENSATE,
    // 本次新增：调整单作为独立写动作，与人工修订（版本链接）区分。
    ADJUSTMENT
}
