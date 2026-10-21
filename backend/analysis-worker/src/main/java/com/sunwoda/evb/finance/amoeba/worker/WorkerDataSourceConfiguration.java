package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/** Creates the worker datasource only for the explicit db profile. */
@Configuration
@Profile("db")
@EnableTransactionManagement
@EnableConfigurationProperties(DataSourceProperties.class)
public class WorkerDataSourceConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        // 本次接线：为 Outbox 人工动作和成功/失败 CAS 提供 Worker 侧 JDBC 事务管理器。
        return new DataSourceTransactionManager(dataSource);
    }
}
