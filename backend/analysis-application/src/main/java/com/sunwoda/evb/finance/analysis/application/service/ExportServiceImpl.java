package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportJobStatus;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Export application orchestration boundary.
 *
 * <p>Authorization is evaluated before the task port is touched. The only
 * identity accepted here is the caller-supplied authenticated identity; the
 * request object intentionally cannot carry one.</p>
 */
@Service
public class ExportServiceImpl implements ExportService {
    private final ExportPermissionPolicy permissionPolicy;
    private final ExportTaskPort taskPort;

    public ExportServiceImpl(ExportPermissionPolicy permissionPolicy,
                             ExportTaskPort taskPort) {
        if (permissionPolicy == null) {
            throw new IllegalArgumentException("permissionPolicy must not be null");
        }
        if (taskPort == null) {
            throw new IllegalArgumentException("taskPort must not be null");
        }
        this.permissionPolicy = permissionPolicy;
        this.taskPort = taskPort;
    }

    @Override
    public ExportJob create(String authenticatedUserId, ExportRequest request) {
        String userId = required(authenticatedUserId, "authenticatedUserId");
        if (request == null) throw new IllegalArgumentException("request 不能为空");

        // This must remain before create(): a denied request must not allocate
        // a task, reveal task-port behavior, or reach file-related code.
        ExportPermission permission = permissionPolicy.authorize(userId,
                request.getPerspective(), request.getResultStatus(),
                request.getRequestedScopes());
        if (!permission.isAllowed()) {
            throw new ExportAuthorizationException(permission.getDenialReason());
        }

        ExportTaskSnapshot snapshot = request.snapshot(permission.getSummaryScopes(),
                permission.getDetailScopes());
        return taskPort.create(userId, request.getRequestKey(), snapshot);
    }

    @Override
    public Optional<ExportJob> status(String authenticatedUserId, String jobId) {
        return taskPort.status(required(jobId, "jobId"),
                required(authenticatedUserId, "authenticatedUserId"));
    }

    @Override
    public Optional<ExportFile> download(String authenticatedUserId, String jobId) {
        String userId = required(authenticatedUserId, "authenticatedUserId");
        String taskId = required(jobId, "jobId");

        // 本次修改：下载前重新读取归属任务，确保当前身份只能触达本人且仍处于已完成、未过期状态。
        Optional<ExportJob> ownedJob = taskPort.status(taskId, userId);
        if (!ownedJob.isPresent() || !isDownloadableOwnedJob(ownedJob.get(), userId)) {
            return Optional.empty();
        }

        // 本次修改：按创建时冻结的结果视角、状态和授权范围重新校验当前导出权限，防止权限撤销后继续下载。
        Optional<ExportTaskSnapshot> snapshot = taskPort.snapshot(taskId);
        if (snapshot.isPresent() && !isCurrentlyAuthorized(userId, snapshot.get())) {
            return Optional.empty();
        }
        // 本次修改：历史任务没有输入快照时，保留既有的本人、完成和未过期下载兼容行为。

        // 本次修改：所有门禁通过后才访问文件端口，继续复用既有下载实现和统一空结果错误协议。
        return taskPort.download(taskId, userId);
    }

    private boolean isDownloadableOwnedJob(ExportJob job, String userId) {
        return job != null
                && userId.equals(job.getUserId())
                && job.getStatus() == ExportJobStatus.SUCCEEDED
                && !job.isExpired(LocalDateTime.now());
    }

    private boolean isCurrentlyAuthorized(String userId, ExportTaskSnapshot snapshot) {
        if (snapshot == null || snapshot.getSummaryScopes().isEmpty()) {
            return false;
        }
        // 本次修改：快照中的明细范围必须属于其汇总范围，异常快照按拒绝处理，避免范围扩大。
        if (!snapshot.getSummaryScopes().contains("ALL")
                && !snapshot.getSummaryScopes().containsAll(snapshot.getDetailScopes())) {
            return false;
        }

        ExportPermission currentPermission = permissionPolicy.authorize(userId,
                snapshot.getPerspective(), snapshot.getResultStatus(),
                snapshot.getSummaryScopes());
        if (!currentPermission.isAllowed()) {
            return false;
        }
        // 本次修改：逐项核对汇总和明细范围，确保当前权限没有覆盖快照外的内容，也没有丢失快照内的授权。
        for (String scope : snapshot.getSummaryScopes()) {
            if (!currentPermission.canExportSummary(scope)) {
                return false;
            }
        }
        for (String scope : snapshot.getDetailScopes()) {
            if (!currentPermission.canExportDetail(scope)) {
                return false;
            }
        }
        return true;
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
