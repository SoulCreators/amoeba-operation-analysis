package com.sunwoda.evb.finance.analysis.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：一次性返回并保存用户角色与数据范围授权。 */
public final class UserAuthorization {
    private final String userId;
    private final List<String> roleCodes;
    private final List<UserScope> scopes;

    public UserAuthorization(String userId, List<String> roleCodes, List<UserScope> scopes) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        this.userId = userId.trim();
        this.roleCodes = immutable(roleCodes);
        this.scopes = scopes == null ? Collections.<UserScope>emptyList()
                : Collections.unmodifiableList(new ArrayList<UserScope>(scopes));
    }

    public String getUserId() { return userId; }
    public List<String> getRoleCodes() { return roleCodes; }
    public List<UserScope> getScopes() { return scopes; }

    private static List<String> immutable(List<String> source) {
        if (source == null) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        for (String value : source) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("角色编码不能为空");
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            if (!result.contains(normalized)) result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }
}
