package com.sunwoda.evb.finance.analysis.domain.repository;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;

import java.util.List;
import java.util.Optional;

/** 异常工作台工作项仓储：按任务整期替换，支持按主键定位与单条状态更新。 */
public interface ExceptionWorkItemRepository {
    List<ExceptionWorkItem> replaceByTaskNo(String taskNo, List<ExceptionWorkItem> items);
    List<ExceptionWorkItem> findByTaskNo(String taskNo);
    Optional<ExceptionWorkItem> findById(Long id);
    ExceptionWorkItem update(ExceptionWorkItem item);
}
