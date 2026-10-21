package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;

import java.util.Set;

/**
 * 计算版本实际业务范围解析端口。
 *
 * <p>本次新增：人工修订必须基于来源版本实际涉及的 scope 做总部财经权限判断；
 * 没有可审计的范围解析实现时，授权器必须拒绝操作，不得退化为仅按视角放行。</p>
 */
public interface CalculationVersionScopeResolver {
    Set<String> resolve(CalculationVersion sourceVersion);
}
