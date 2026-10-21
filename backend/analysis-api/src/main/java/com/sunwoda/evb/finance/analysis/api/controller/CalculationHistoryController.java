package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.ManualCalculationRevisionRequest;
import com.sunwoda.evb.finance.analysis.application.service.ManualCalculationRevisionService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 计算结果历史版本查询和人工修订 HTTP 入口。
 *
 * <p>本次修改：新增最小 API 切片，不改变已有 CalculationController 的确认/发布接口。
 * 历史查询只调用读取端口；人工修订使用认证用户、强制原因和 requestKey，
 * 并在进入应用服务前再次校验人工修订权限。</p>
 */
@RestController
@RequestMapping("/api/v1/calculations")
public class CalculationHistoryController {
    private CalculationVersionRepository versionRepository;
    private PermissionService permissionService;
    private ManualCalculationRevisionService revisionService;

    /**
     * 本次修改：保留无参构造，避免当前尚未完成基础设施接线时影响既有 API 启动；
     * 真实运行环境由配置注入三个端口，缺失时接口明确返回未接线错误。
     */
    public CalculationHistoryController() {
    }

    /** Controller 契约测试使用的显式依赖构造器。 */
    public CalculationHistoryController(CalculationVersionRepository versionRepository,
                                        PermissionService permissionService,
                                        ManualCalculationRevisionService revisionService) {
        this.versionRepository = requiredDependency(versionRepository, "versionRepository");
        this.permissionService = requiredDependency(permissionService, "permissionService");
        this.revisionService = requiredDependency(revisionService, "revisionService");
    }

    /**
     * 本次修改：通过可选 Setter 接入已经存在的 Spring Bean；不创建 infrastructure 替身，
     * 因而本切片不会偷偷改变生产数据存储方式。
     */
    @Autowired(required = false)
    public void configure(CalculationVersionRepository versionRepository,
                          PermissionService permissionService,
                          ManualCalculationRevisionService revisionService) {
        this.versionRepository = versionRepository;
        this.permissionService = permissionService;
        this.revisionService = revisionService;
    }

    /**
     * 本次修改：按批次和分析视角读取完整版本链；权限只允许 ADMIN 或授权总部财经读取，
     * 不修改当前版本指针，也不读取请求体中的伪造用户。
     */
    @GetMapping("/{batchNo}/versions")
    public ApiResult<List<CalculationVersion>> versions(@PathVariable String batchNo,
                                                        @RequestParam String perspective,
                                                        Principal principal) {
        String userId = requiredUser(principal);
        String requiredBatchNo = required(batchNo, "batchNo不能为空");
        AnalysisPerspective requiredPerspective = AnalysisPerspective.fromCode(
                required(perspective, "perspective不能为空"));
        ensureWired();
        if (!permissionService.canViewHistory(userId, requiredPerspective)) {
            throw new IllegalStateException("当前用户无历史版本查看权限");
        }
        List<CalculationVersion> versions = versionRepository.findByBatchNo(
                requiredBatchNo, requiredPerspective);
        if (versions == null || versions.isEmpty()) {
            return ApiResult.ok(Collections.<CalculationVersion>emptyList());
        }
        // 本次修改：复制仓储结果后再返回，避免 HTTP 层暴露可变列表导致查询结果被调用方改写。
        return ApiResult.ok(Collections.unmodifiableList(
                new ArrayList<CalculationVersion>(versions)));
    }

    /**
     * 本次修改：追加一个人工修订版本。requestKey 的作用域包含认证用户，
     * 同 key 同载荷重放原结果；同 key 不同来源或原因直接拒绝，避免重复提交生成新版本。
     */
    @PostMapping("/revisions")
    public ApiResult<CalculationVersion> revise(@RequestBody ManualCalculationRevisionRequest request,
                                                Principal principal) {
        String userId = requiredUser(principal);
        if (request == null) {
            throw new IllegalArgumentException("人工修订请求不能为空");
        }
        String sourceVersionId = required(request.getSourceVersionId(), "sourceVersionId不能为空");
        String revisionReason = required(request.getRevisionReason(), "revisionReason不能为空");
        String requestKey = required(request.getRequestKey(), "requestKey不能为空");
        ensureWired();

        CalculationVersion source = versionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new IllegalArgumentException("来源计算版本不存在: " + sourceVersionId));
        if (!permissionService.canManualRevise(userId, source.getPerspective())) {
            throw new IllegalStateException("当前用户无历史版本人工修订权限");
        }

        String cacheKey = userId + "\u0000" + requestKey;
        String fingerprint = sourceVersionId + "\u0000" + revisionReason;
        synchronized (replayCache) {
            RevisionReplay replay = replayCache.get(cacheKey);
            if (replay != null) {
                if (!replay.fingerprint.equals(fingerprint)) {
                    throw new IllegalArgumentException("相同 requestKey 不允许提交不同人工修订内容");
                }
                return ApiResult.ok(replay.result);
            }
            CalculationVersion result = revisionService.revise(
                    sourceVersionId, userId, revisionReason);
            replayCache.put(cacheKey, new RevisionReplay(fingerprint, result));
            return ApiResult.ok(result);
        }
    }

    private final Map<String, RevisionReplay> replayCache =
            new ConcurrentHashMap<String, RevisionReplay>();

    private void ensureWired() {
        if (versionRepository == null || permissionService == null || revisionService == null) {
            throw new IllegalStateException("历史版本接口尚未完成运行时接线");
        }
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null
                || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private <T> T requiredDependency(T dependency, String name) {
        if (dependency == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return dependency;
    }

    private static final class RevisionReplay {
        private final String fingerprint;
        private final CalculationVersion result;

        private RevisionReplay(String fingerprint, CalculationVersion result) {
            this.fingerprint = fingerprint;
            this.result = result;
        }
    }
}
