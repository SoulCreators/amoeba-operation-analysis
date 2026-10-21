package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.adapter.CalculationServiceRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationJobRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationRetryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 失败处理 Worker 的新增接线配置。
 *
 * <p>本次新增：db profile 下接入 V025 作业仓储和 V028 两个仓储；只有部署方提供
 * CalculationRetryExecutor 时才创建 Worker，避免改变现有 Worker 默认启动行为。</p>
 */
@Configuration
@Profile("db")
@EnableScheduling
@Import({JdbcCalculationJobRepository.class, JdbcCalculationRetryRepository.class,
        JdbcCalculationRetryActionRepository.class})
public class CalculationRetryWorkerConfiguration {

    @Bean
    public CalculationRetryApplicationService calculationRetryApplicationService(
            CalculationJobRepository jobRepository,
            CalculationRetryRepository retryRepository,
            CalculationRetryActionRepository actionRepository) {
        return new CalculationRetryApplicationService(jobRepository, retryRepository, actionRepository);
    }

    @Bean
    @ConditionalOnBean(CalculationService.class)
    public CalculationRetryExecutor calculationRetryExecutor(CalculationService calculationService) {
        // 本次新增：已有计算服务存在时，才自动适配为失败处理执行端口。
        return new CalculationServiceRetryExecutor(calculationService);
    }

    @Bean
    @ConditionalOnBean(CalculationRetryExecutor.class)
    public CalculationJobRetryWorker calculationJobRetryWorker(
            CalculationRetryApplicationService retryService,
            CalculationRetryExecutor executor) {
        return new CalculationJobRetryWorker(retryService, executor);
    }

    @Bean
    @ConditionalOnBean(CalculationJobRetryWorker.class)
    public CalculationRetryScheduledWorker calculationRetryScheduledWorker(
            CalculationJobRetryWorker worker,
            @Value("${analysis.worker.calculation-retry.batch-size:100}") int batchSize) {
        return new CalculationRetryScheduledWorker(worker, batchSize);
    }
}
