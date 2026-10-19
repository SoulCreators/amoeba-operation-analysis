package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportFile;
import com.sunwoda.evb.finance.analysis.application.model.ExportJob;

import java.util.Optional;

/**
 * 导出任务应用端口。
 *
 * <p>状态和下载接口均要求调用方同时提供 jobId 与 userId。未找到任务、任务不属于
 * 当前用户、任务尚未完成或文件不存在时统一返回空结果，避免泄露未授权文件和任务存在性。</p>
 */
public interface ExportTaskPort {
    ExportJob create(String userId, String requestKey, String fileName, String contentType);

    Optional<ExportJob> status(String jobId, String userId);

    Optional<ExportFile> download(String jobId, String userId);

    void markRunning(String jobId);

    void complete(String jobId, String fileName, String contentType, byte[] content);

    void fail(String jobId, String errorMessage);
}
