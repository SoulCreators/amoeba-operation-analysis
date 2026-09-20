package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class InMemoryUserScopeRepository implements UserScopeRepository {
    private final Map<String, UserScope> data = new LinkedHashMap<String, UserScope>();

    @Override
    public synchronized UserScope save(UserScope scope) {
        data.put(key(scope), scope);
        return scope;
    }

    @Override
    public synchronized List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective) {
        List<UserScope> result = new ArrayList<UserScope>();
        for (UserScope scope : data.values()) {
            if (scope.isEnabled() && scope.getUserId().equals(userId)
                    && scope.getPerspective() == perspective) result.add(scope);
        }
        return Collections.unmodifiableList(result);
    }

    private String key(UserScope scope) {
        return scope.getUserId() + "|" + scope.getRoleCode() + "|"
                + scope.getPerspective().getCode() + "|" + scope.getScopeCode();
    }
}
