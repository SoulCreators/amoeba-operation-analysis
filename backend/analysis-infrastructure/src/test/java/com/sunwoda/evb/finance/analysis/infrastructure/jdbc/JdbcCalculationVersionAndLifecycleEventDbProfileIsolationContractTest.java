package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.repository.CalculationLifecycleEventRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DATA-004-TECH-003-REVISION-003：验证版本头和生命周期 Outbox JDBC 适配器的 Spring 接线契约。
 *
 * <p>本次新增测试只锁定 db profile、Repository 和 Primary 边界，不改变 API/application 行为。</p>
 */
class JdbcCalculationVersionAndLifecycleEventDbProfileIsolationContractTest {

    @Test
    void calculationVersionJdbcRepositoryUsesDbProfileAndPrimary() {
        // 本次新增契约：确保版本头 JDBC 实现只在 db profile 生效，并优先于内存实现。
        assertTrue(CalculationVersionRepository.class.isAssignableFrom(
                JdbcCalculationVersionRepository.class));
        Repository repository = JdbcCalculationVersionRepository.class.getAnnotation(Repository.class);
        assertNotNull(repository);
        assertEquals("", repository.value());
        Profile profile = JdbcCalculationVersionRepository.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"db"}, profile.value());
        assertNotNull(JdbcCalculationVersionRepository.class.getAnnotation(Primary.class));
    }

    @Test
    void lifecycleEventJdbcRepositoryUsesDbProfileAndPrimary() {
        // 本次新增契约：确保 Outbox JDBC 实现与版本仓储使用同一 db/Primary 接线规则。
        assertTrue(CalculationLifecycleEventRepository.class.isAssignableFrom(
                JdbcCalculationLifecycleEventRepository.class));
        Repository repository = JdbcCalculationLifecycleEventRepository.class.getAnnotation(Repository.class);
        assertNotNull(repository);
        assertEquals("", repository.value());
        Profile profile = JdbcCalculationLifecycleEventRepository.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"db"}, profile.value());
        assertNotNull(JdbcCalculationLifecycleEventRepository.class.getAnnotation(Primary.class));
    }
}
