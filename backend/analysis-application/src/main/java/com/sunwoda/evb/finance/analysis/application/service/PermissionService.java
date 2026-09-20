package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;

import java.util.List;

public interface PermissionService {
    UserScope save(UserScope scope);
    List<UserScope> list(String userId, AnalysisPerspective perspective);
    boolean canViewScope(String userId, AnalysisPerspective perspective, String scopeCode);
}
