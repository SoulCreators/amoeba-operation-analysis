package com.sunwoda.evb.finance.analysis.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DATA-004-TECH-003 confirm/publish 事务边界契约。
 *
 * <p>本次新增测试：确认生产服务的两个生命周期写入口均声明事务，保证 db profile 可由 Spring 事务代理执行。
 * 真实 PostgreSQL 提交/回滚由基础设施契约另行验收。</p>
 */
class CalculationServiceTransactionBoundaryContractTest {
    @Test
    void confirmAndPublishMustDeclareTransactionalBoundary() throws NoSuchMethodException {
        Method confirm = CalculationServiceImpl.class.getMethod("confirm", String.class, String.class);
        Method publish = CalculationServiceImpl.class.getMethod("publish", String.class, String.class);

        assertNotNull(confirm.getAnnotation(Transactional.class), "confirm 必须声明事务");
        assertNotNull(publish.getAnnotation(Transactional.class), "publish 必须声明事务");
    }
}
