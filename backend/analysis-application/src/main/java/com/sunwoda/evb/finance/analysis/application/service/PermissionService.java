package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
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
    boolean canViewResult(String userId, AnalysisPerspective perspective,
                          CalculationResultStatus status);
    boolean canPublishAi(String userId, AnalysisPerspective perspective);
    Set<String> viewableScopes(String userId, AnalysisPerspective perspective);
    Set<String> detailScopes(String userId, AnalysisPerspective perspective);
    Set<String> exportScopes(String userId, AnalysisPerspective perspective);
}
