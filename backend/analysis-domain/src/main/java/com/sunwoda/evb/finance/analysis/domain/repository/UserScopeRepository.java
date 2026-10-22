package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.List;
import java.util.ArrayList;

public interface UserScopeRepository {
    UserScope save(UserScope scope);
    List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective);

    /** 本次新增：IAM 管理中心按用户读取三类视角，旧实现自动兼容。 */
    default List<UserScope> findByUser(String userId) {
        List<UserScope> result = new ArrayList<UserScope>();
        for (AnalysisPerspective perspective : AnalysisPerspective.values()) {
            List<UserScope> current = findByUserAndPerspective(userId, perspective);
            if (current != null) result.addAll(current);
        }
        return result;
    }

    /** 本次新增：整组替换采用停用旧授权再启用新授权；不物理删除历史行。 */
    default void replaceForUser(String userId, List<UserScope> scopes) {
        throw new UnsupportedOperationException("当前权限仓储不支持整组替换");
    }
}
