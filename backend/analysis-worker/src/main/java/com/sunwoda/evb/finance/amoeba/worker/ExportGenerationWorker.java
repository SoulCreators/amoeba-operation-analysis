package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.service.ExportGenerationService;

/**
 * 导出生成 Worker 入口。
 *
 * <p>Worker 只负责校验并转交 jobId，实际任务快照读取、生命周期门禁、渲染和
 * 完成/失败编排均委托给应用层 {@link ExportGenerationService}，不在本类重复实现。</p>
 *
 * <p>本类刻意不添加 {@code @Scheduled} 和 Spring profile 注解：当前没有可靠的
 * 任务领取/消息触发端口，增加定时入口会引入轮询并改变现有 Worker 的默认行为。
 * 待任务触发机制落地后，再由应用启动配置按实际触发机制注册本入口。</p>
 */
public final class ExportGenerationWorker {
    private final ExportGenerationService generationService;

    /**
     * 构造一个委托应用服务的 Worker 入口。
     *
     * @param generationService 导出生成应用服务
     */
    public ExportGenerationWorker(ExportGenerationService generationService) {
        if (generationService == null) {
            throw new IllegalArgumentException("generationService must not be null");
        }
        this.generationService = generationService;
    }

    /**
     * 处理一个指定的导出任务，并把任务号交给应用服务完成实际生成。
     *
     * @param jobId 导出任务号
     */
    public void process(String jobId) {
        generationService.generate(required(jobId, "jobId"));
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
