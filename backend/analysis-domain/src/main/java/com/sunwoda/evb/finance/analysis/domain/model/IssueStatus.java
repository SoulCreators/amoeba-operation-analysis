package com.sunwoda.evb.finance.analysis.domain.model;

/** 异常工作项处理状态。复检（重新校验）会把已忽略警告重新置为未处理。 */
public enum IssueStatus {
    OPEN,
    IGNORED
}
