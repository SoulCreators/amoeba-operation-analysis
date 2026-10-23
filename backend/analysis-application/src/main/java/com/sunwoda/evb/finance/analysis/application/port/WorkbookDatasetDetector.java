package com.sunwoda.evb.finance.analysis.application.port;

import java.util.List;
import java.util.Set;

/** 识别整本工作簿中包含的数据集（按表名定位），供整本导入拆分使用。 */
public interface WorkbookDatasetDetector {
    /**
     * 返回 {@code candidateCodes} 中在整本工作簿里能稳定定位到数据表的编码子集。
     * 无法唯一识别的数据集（如研发、实验室制造体系）不返回，避免误建任务。
     */
    Set<String> detectPresentDatasets(byte[] content, List<String> candidateCodes);
}
