package com.sunwoda.evb.finance.amoeba.worker;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/** Creates the worker datasource only for the explicit db profile. */
@Configuration
@Profile("db")
@EnableConfigurationProperties(DataSourceProperties.class)
public class WorkerDataSourceConfiguration {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
