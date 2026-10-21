package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.ManualCalculationRevisionRequest;
import com.sunwoda.evb.finance.analysis.application.port.ManualRevisionIdempotencyPort;
import com.sunwoda.evb.finance.analysis.application.service.ManualCalculationRevisionService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.context.annotation.Profile;
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

/**
 * 计算结果历史版本查询和人工修订 HTTP 入口。
 *
 * <p>本次修改：新增最小 API 切片，不改变已有 CalculationController 的确认/发布接口。
 * 历史查询只调用读取端口；人工修订使用认证用户、强制原因和 requestKey，
 * 并在进入应用服务前再次校验人工修订权限。</p>
 */
@RestController
// 本次修改：历史版本和人工修订依赖持久化仓储，仅在 db Profile 暴露；local 不提供伪造的历史接口。
@Profile("db")
@RequestMapping("/api/v1/calculations")
public class CalculationHistoryController {
    private final CalculationVersionRepository versionRepository;
    private final PermissionService permissionService;
    private final ManualCalculationRevisionService revisionService;
    private final ManualRevisionIdempotencyPort idempotencyPort;

    /**
     * 本次修改：只保留必需构造器注入，local/db profile 缺少任何生产依赖时不创建接口 Bean；
     * 不再通过无参构造、可选 Setter 或空缓存伪造运行时接线。
     */
    public CalculationHistoryController(CalculationVersionRepository versionRepository,
                                        PermissionService permissionService,
                                        ManualCalculationRevisionService revisionService,
                                        ManualRevisionIdempotencyPort idempotencyPort) {
        this.versionRepository = requiredDependency(versionRepository, "versionRepository");
        this.permissionService = requiredDependency(permissionService, "permissionService");
        this.revisionService = requiredDependency(revisionService, "revisionService");
        this.idempotencyPort = requiredDependency(idempotencyPort, "idempotencyPort");
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
     * 本次修改：追加一个人工修订版本；requestKey 的原子领取和结果重放交给持久化应用端口，
     * Controller 不再维护进程内缓存，未接入持久实现时由必需依赖失败关闭。
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
        CalculationVersion source = versionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new IllegalArgumentException("来源计算版本不存在: " + sourceVersionId));
        if (!permissionService.canManualRevise(userId, source.getPerspective())) {
            throw new IllegalStateException("当前用户无历史版本人工修订权限");
        }

        CalculationVersion result = idempotencyPort.execute(userId, requestKey, sourceVersionId,
                revisionReason, new ManualRevisionIdempotencyPort.RevisionOperation() {
                    @Override
                    public CalculationVersion execute() {
                        return revisionService.revise(sourceVersionId, userId, revisionReason);
                    }
                });
        return ApiResult.ok(result);
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

}
