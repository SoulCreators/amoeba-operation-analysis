package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.port.LocalAiGateway;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResult;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationResultStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.AiAnalysisDraftRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {
    private final CalculationService calculationService;
    private final LocalAiGateway gateway;
    private final AnalysisBatchRepository batchRepository;
    private final PermissionService permissionService;
    private final Map<String, AiAnalysisDraft> drafts = new LinkedHashMap<String, AiAnalysisDraft>();
    private final AiAnalysisDraftRepository draftRepository;

    @Autowired
    public AiAnalysisServiceImpl(CalculationService calculationService, LocalAiGateway gateway,
                                 AnalysisBatchRepository batchRepository,
                                 PermissionService permissionService,
                                 AiAnalysisDraftRepository draftRepository) {
        this.calculationService = calculationService;
        this.gateway = gateway;
        this.batchRepository = batchRepository;
        this.permissionService = permissionService;
        this.draftRepository = draftRepository;
    }

    /** 保留原有基于 UserScopeRepository 的应用层构造调用。 */
    public AiAnalysisServiceImpl(CalculationService calculationService, LocalAiGateway gateway,
                                 AnalysisBatchRepository batchRepository,
                                 UserScopeRepository scopeRepository,
                                 AiAnalysisDraftRepository draftRepository) {
        this(calculationService, gateway, batchRepository,
                new PermissionServiceImpl(scopeRepository), draftRepository);
    }

    public AiAnalysisServiceImpl(CalculationService calculationService, LocalAiGateway gateway,
                                 AnalysisBatchRepository batchRepository,
                                 UserScopeRepository scopeRepository) {
        this.calculationService = calculationService;
        this.gateway = gateway;
        this.batchRepository = batchRepository;
        this.permissionService = new PermissionServiceImpl(scopeRepository);
        this.draftRepository = null;
    }

    @Override
    public synchronized AiAnalysisDraft generate(String batchNo, String userId) {
        CalculationResult result = calculationService.getForUser(batchNo, userId);
        if (result.getStatus() != CalculationResultStatus.CONFIRMED
                && result.getStatus() != CalculationResultStatus.PUBLISHED) {
            throw new IllegalStateException("只有已确认或已发布结果可以生成AI分析: " + result.getStatus());
        }
        AiAnalysisDraft draft = AiAnalysisDraft.draft(batchNo, result.getVersionNo(),
                gateway.generate(result), userId);
        save(draft);
        return draft;
    }

    @Override
    public synchronized AiAnalysisDraft get(String batchNo, String userId) {
        // 先通过结果权限校验，再读取草稿，避免未授权用户探测草稿是否存在。
        calculationService.getForUser(batchNo, userId);
        AiAnalysisDraft draft = draftRepository == null
                ? drafts.get(batchNo) : draftRepository.findByBatchNo(batchNo).orElse(null);
        if (draft == null) throw new IllegalArgumentException("AI分析尚未生成: " + batchNo);
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
        return permissionService.canPublishAi(userId, perspective);
    }
}
