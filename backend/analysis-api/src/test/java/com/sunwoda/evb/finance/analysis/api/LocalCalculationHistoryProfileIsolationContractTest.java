package com.sunwoda.evb.finance.analysis.api;

import com.sunwoda.evb.finance.analysis.api.controller.CalculationHistoryController;
import com.sunwoda.evb.finance.analysis.application.service.ManualCalculationRevisionService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionBasedManualRevisionAuthorizer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * local Profile 下历史版本和人工修订持久化边界契约。
 *
 * <p>本次新增测试：历史版本 HTTP 入口、人工修订服务和授权器都必须只绑定 db；
 * local 没有生产 JDBC 仓储时不注册半套依赖，避免应用上下文启动失败或暴露不可用接口。</p>
 */
class LocalCalculationHistoryProfileIsolationContractTest {
    @Test
    void historyAndRevisionComponentsAreDatabaseProfileOnly() {
        // 本次新增：直接锁定三个组件的 Profile，防止后续误把持久化能力重新开放到 local。
        assertArrayEquals(new String[]{"db"},
                CalculationHistoryController.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[]{"db"},
                ManualCalculationRevisionService.class.getAnnotation(Profile.class).value());
        assertArrayEquals(new String[]{"db"},
                PermissionBasedManualRevisionAuthorizer.class.getAnnotation(Profile.class).value());
    }
}
