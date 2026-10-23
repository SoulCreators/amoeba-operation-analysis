package com.sunwoda.evb.finance.analysis.domain.model;

/**
 * 源文件版本的校验状态。
 *
 * <p>区别于 {@link ImportStatus} 的导入任务生命周期，本枚举只描述单个源文件版本
 * 的校验结论：解析校验通过前为 PENDING，通过后为 VALID，被阻断为 INVALID。</p>
 */
public enum SourceFileValidationStatus {
    PENDING,
    VALID,
    INVALID
}
