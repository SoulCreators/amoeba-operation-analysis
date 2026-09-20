package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {
    private final CalculationService calculationService;
    private final LocalAiGateway gateway;
    private final AnalysisBatchRepository batchRepository;
    private final UserScopeRepository scopeRepository;
    private final Map<String, AiAnalysisDraft> drafts = new LinkedHashMap<String, AiAnalysisDraft>();
    private final AiAnalysisDraftRepository draftRepository;

    @Autowired
    public AiAnalysisServiceImpl(CalculationService calculationService, LocalAiGateway gateway,
                                 AnalysisBatchRepository batchRepository,
                                 UserScopeRepository scopeRepository,
                                 AiAnalysisDraftRepository draftRepository) {
        this.calculationService = calculationService;
        this.gateway = gateway;
        this.batchRepository = batchRepository;
        this.scopeRepository = scopeRepository;
        this.draftRepository = draftRepository;
    }

    public AiAnalysisServiceImpl(CalculationService calculationService, LocalAiGateway gateway,
                                 AnalysisBatchRepository batchRepository,
                                 UserScopeRepository scopeRepository) {
        this.calculationService = calculationService;
        this.gateway = gateway;
        this.batchRepository = batchRepository;
        this.scopeRepository = scopeRepository;
        this.draftRepository = null;
    }

    @Override
    public synchronized AiAnalysisDraft generate(String batchNo, String userId) {
        CalculationResult result = calculationService.getForUser(batchNo, userId);
        AiAnalysisDraft draft = AiAnalysisDraft.draft(batchNo, result.getVersionNo(),
                gateway.generate(result), userId);
        save(draft);
        return draft;
    }

    @Override
    public synchronized AiAnalysisDraft get(String batchNo, String userId) {
        AiAnalysisDraft draft = draftRepository == null
                ? drafts.get(batchNo) : draftRepository.findByBatchNo(batchNo).orElse(null);
        if (draft == null) throw new IllegalArgumentException("AI分析尚未生成: " + batchNo);
        calculationService.getForUser(batchNo, userId);
        return draft;
    }

    @Override
    public synchronized AiAnalysisDraft publish(String batchNo, String userId) {
        AiAnalysisDraft draft = get(batchNo, userId);
        CalculationResult result = calculationService.getForUser(batchNo, userId);
        if (!canPublish(userId, result.getPerspective())) {
            throw new IllegalStateException("当前用户无AI分析发布权限");
        }
        AiAnalysisDraft published = draft.publish(userId);
        save(published);
        return published;
    }

    private void save(AiAnalysisDraft draft) {
        if (draftRepository == null) drafts.put(draft.getBatchNo(), draft);
        else draftRepository.save(draft);
    }

    private boolean canPublish(String userId, AnalysisPerspective perspective) {
        List<UserScope> scopes = scopeRepository.findByUserAndPerspective(userId, perspective);
        for (UserScope scope : scopes) {
            String role = scope.getRoleCode().toUpperCase(java.util.Locale.ROOT);
            if (role.contains("FINANCE") || role.contains("ADMIN")) return true;
        }
        return false;
    }
}
