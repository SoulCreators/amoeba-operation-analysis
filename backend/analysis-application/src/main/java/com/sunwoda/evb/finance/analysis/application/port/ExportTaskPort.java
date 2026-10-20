package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;

import java.util.Optional;

/**
 * 导出任务应用端口。
 *
 * <p>状态和下载接口均要求调用方同时提供 jobId 与 userId。未找到任务、任务不属于
 * 当前用户、任务尚未完成或文件不存在时统一返回空结果，避免泄露未授权文件和任务存在性。</p>
 */
public interface ExportTaskPort {
    ExportJob create(String userId, String requestKey, String fileName, String contentType);

    /**
     * Creates a task with the authorization result and result identity frozen
     * for the eventual worker.  The legacy overload remains available for
     * existing callers that only manage task metadata.
     */
    default ExportJob create(String userId, String requestKey,
                             ExportTaskSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot 不能为空");
        return create(userId, requestKey, snapshot.fileName(), snapshot.contentType());
    }

    /** Reads the immutable worker input, when this task was created with one. */
    default Optional<ExportTaskSnapshot> snapshot(String jobId) {
        return Optional.empty();
    }

    Optional<ExportJob> status(String jobId, String userId);

    Optional<ExportFile> download(String jobId, String userId);

    void markRunning(String jobId);

    /**
     * 尝试原子领取导出任务；默认委托旧的 markRunning，兼容尚未实现原子领取的端口，
     * 但这类旧端口只能返回成功，不能提供多 Worker 抢占判定能力。
     */
    default boolean tryMarkRunning(String jobId) {
        markRunning(jobId);
        return true;
    }

    void complete(String jobId, String fileName, String contentType, byte[] content);

    void fail(String jobId, String errorMessage);
}
