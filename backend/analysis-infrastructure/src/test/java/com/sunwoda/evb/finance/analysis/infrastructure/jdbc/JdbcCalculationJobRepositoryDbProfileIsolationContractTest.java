package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CALC-001-JDBC-ADAPTER-001：计算作业 JDBC 仓储的 Spring 接线契约。
 *
 * <p>本次新增测试只锁定 db profile、Repository 和 Primary 边界，
 * 不改变计算服务、Worker、调度、重试、版本或权限语义。</p>
 */
class JdbcCalculationJobRepositoryDbProfileIsolationContractTest {

    @Test
    void jdbcRepositoryExposesCalculationJobPortOnlyInDbProfile() {
        // 本次新增契约：确保 V025 JDBC 适配器只在 db profile 暴露计算作业仓储。
        assertTrue(CalculationJobRepository.class.isAssignableFrom(
                JdbcCalculationJobRepository.class));

        Repository repository = JdbcCalculationJobRepository.class.getAnnotation(Repository.class);
        assertNotNull(repository);
        assertEquals("", repository.value());

        Profile profile = JdbcCalculationJobRepository.class.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[] {"db"}, profile.value());
    }

    @Test
    void jdbcRepositoryIsPrimaryCandidateForCalculationJobPort() {
        // 本次新增契约：确保 db profile 下 JDBC 实现优先于内存实现，避免仓储回退。
        assertNotNull(JdbcCalculationJobRepository.class.getAnnotation(Primary.class));
    }
}
