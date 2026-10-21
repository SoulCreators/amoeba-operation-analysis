package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

/**
 * 历史版本人工修订授权端口。
 *
 * <p>本次修改：把人工修订授权作为独立端口注入，避免改变现有 PermissionServiceImpl
 * 的权限语义；未通过授权时，应用服务不得写入版本、修订或生命周期记录。</p>
 */
public interface ManualRevisionAuthorizer {
    void assertAuthorized(String reviser, CalculationVersion sourceVersion);
}
