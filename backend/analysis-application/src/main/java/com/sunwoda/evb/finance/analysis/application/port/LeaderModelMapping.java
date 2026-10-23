package com.sunwoda.evb.finance.analysis.application.port;

import java.util.Set;

/**
 * 非财经巴长归属映射端口：按电芯型号解析有效巴长工号。
 *
 * <p>一个型号一个有效巴长，一个巴长多个型号。解析出多个巴长即为冲突，阻断非财经发布。
 * 生产实现依赖通用销售项目映射（FR-CFG-001），此处以端口隔离。</p>
 */
public interface LeaderModelMapping {
    /** 返回某型号映射的全部巴长工号；无映射返回空集。 */
    Set<String> leadersOf(String cellModel);

    /** 返回某巴长工号映射的全部型号；无映射返回空集。 */
    Set<String> modelsOf(String leaderEmployeeNo);
}
