package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.ExportGenerationResult;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;
import com.sunwoda.evb.finance.analysis.application.port.ExportGenerationRenderer;
import com.sunwoda.evb.finance.analysis.application.port.ExportTaskPort;

import java.util.Optional;

/**
 * 导出生成应用编排服务。
 *
 * <p>服务按 jobId 读取任务快照并驱动任务生命周期。重复处理、终态和过期任务的门禁
 * 继续由 {@link ExportTaskPort} 的现有实现负责；本服务不复制状态机，也不决定格式、
 * 权限、保留期限、审计词汇或业务公式。</p>
 */
public final class ExportGenerationService {
    private final ExportTaskPort taskPort;
    private final ExportGenerationRenderer renderer;

    /** 注入任务生命周期端口和具体渲染器，保持格式实现与应用编排解耦。 */
    public ExportGenerationService(ExportTaskPort taskPort,
                                   ExportGenerationRenderer renderer) {
        if (taskPort == null) {
            throw new IllegalArgumentException("taskPort 不能为空");
        }
        if (renderer == null) {
            throw new IllegalArgumentException("renderer 不能为空");
        }
        this.taskPort = taskPort;
        this.renderer = renderer;
    }

    /**
     * 按 jobId 执行一次导出生成。
     *
     * <p>快照读取和 tryMarkRunning 失败时不进入渲染异常边界；只有渲染阶段异常才标记
     * 失败并重新抛出，完成阶段异常则交由任务端口的条件更新和终态门禁处理。</p>
     */
    public ExportGenerationResult generate(String jobId) {
        String key = required(jobId, "jobId");
        Optional<ExportTaskSnapshot> snapshot = taskPort.snapshot(key);
        if (!snapshot.isPresent()) {
            throw new IllegalStateException("导出任务缺少渲染快照: " + key);
        }

        // 由任务端口原子领取决定任务能否进入运行态，服务不自行判断或吞掉门禁异常。
        if (!taskPort.tryMarkRunning(key)) {
            throw new IllegalStateException("导出任务已被其他 Worker 领取，不能重复渲染: " + key);
        }

        ExportGenerationResult result;
        try {
            result = renderer.render(snapshot.get());
            if (result == null) {
                throw new IllegalStateException("渲染器返回结果不能为空");
            }
        } catch (RuntimeException renderingFailure) {
            failAfterRenderingFailure(key, renderingFailure);
            throw renderingFailure;
        }

        // 文件名、内容类型和字节全部来自渲染结果，应用编排层不推断具体格式。
        taskPort.complete(key, result.getFileName(), result.getContentType(), result.getContent());
        return result;
    }

    /** 渲染失败时尽力写入失败状态，但不遮蔽原始渲染异常。 */
    private void failAfterRenderingFailure(String jobId, RuntimeException renderingFailure) {
        try {
            taskPort.fail(jobId, failureMessage(renderingFailure));
        } catch (RuntimeException failFailure) {
            renderingFailure.addSuppressed(failFailure);
        }
    }

    private static String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty()
                ? failure.getClass().getName()
                : message.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }
}
