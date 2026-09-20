package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.List;

public interface UserScopeRepository {
    UserScope save(UserScope scope);
    List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective);
}
