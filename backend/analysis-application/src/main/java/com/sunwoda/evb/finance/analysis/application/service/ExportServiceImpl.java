package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportRequest;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;
import org.springframework.stereotype.Service;

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
        return taskPort.download(required(jobId, "jobId"),
                required(authenticatedUserId, "authenticatedUserId"));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
