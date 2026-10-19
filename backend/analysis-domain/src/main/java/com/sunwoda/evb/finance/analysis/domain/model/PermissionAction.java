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
    EDIT_CONFIG
}
