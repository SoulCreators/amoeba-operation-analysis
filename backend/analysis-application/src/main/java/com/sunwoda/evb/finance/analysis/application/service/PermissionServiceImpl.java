package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {
    private final UserScopeRepository repository;

    public PermissionServiceImpl(UserScopeRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserScope save(UserScope scope) {
        return repository.save(scope);
    }

    @Override
    public List<UserScope> list(String userId, AnalysisPerspective perspective) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("用户工号不能为空");
        if (perspective == null) throw new IllegalArgumentException("数据视角不能为空");
        return repository.findByUserAndPerspective(userId.trim(), perspective);
    }

    @Override
    public boolean canViewScope(String userId, AnalysisPerspective perspective, String scopeCode) {
        for (UserScope scope : list(userId, perspective)) {
            if ("ALL".equalsIgnoreCase(scope.getScopeCode())
                    || scope.getScopeCode().equalsIgnoreCase(scopeCode)) return true;
        }
        return false;
    }
}
