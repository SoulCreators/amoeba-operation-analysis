package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.ExportGenerationResult;
import com.sunwoda.evb.finance.analysis.application.model.ExportTaskSnapshot;

/**
 * 导出生成渲染端口。
 *
 * <p>具体实现只负责根据不可变快照生成文件结果；应用编排层不介入具体文件格式、
 * 业务公式或权限判断。</p>
 */
@FunctionalInterface
public interface ExportGenerationRenderer {
    /** 根据任务快照渲染出可交给任务端口发布的结果。 */
    ExportGenerationResult render(ExportTaskSnapshot snapshot);
}
