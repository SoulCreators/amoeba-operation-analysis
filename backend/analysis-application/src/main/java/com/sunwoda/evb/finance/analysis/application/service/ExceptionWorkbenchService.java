package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.ExceptionWorkItem;
import com.sunwoda.evb.finance.analysis.domain.model.IssueSeverity;
import com.sunwoda.evb.finance.analysis.domain.model.IssueStatus;

import java.util.List;

/** 异常工作台：按级别/错误码/对象/处理状态筛选，受控忽略与复检，阻断闭环。 */
public interface ExceptionWorkbenchService {
    List<ExceptionWorkItem> list(String taskNo, IssueSeverity severity, String issueCode,
                                 String objectCode, IssueStatus status);

    /** 受控忽略：仅警告且未处理可忽略，记录忽略人/时间/原因；阻断项拒绝。 */
    ExceptionWorkItem ignore(String taskNo, Long issueId, String reason, String operator);

    /** 修复复检：把已忽略警告重新置为未处理，供新一轮校验重新判定。 */
    List<ExceptionWorkItem> recheck(String taskNo, String operator);

    /** 阻断闭环：存在未处理的阻断项时返回 true，发布/确认门禁应据此阻断。 */
    boolean hasBlockingOpen(String taskNo);
}
