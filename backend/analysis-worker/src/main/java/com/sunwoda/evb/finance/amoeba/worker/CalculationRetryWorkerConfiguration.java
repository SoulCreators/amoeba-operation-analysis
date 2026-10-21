package com.sunwoda.evb.finance.amoeba.worker;

import com.sunwoda.evb.finance.analysis.application.adapter.CalculationServiceRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import com.sunwoda.evb.finance.analysis.application.port.CalculationRetryExecutor;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactProvider;
import com.sunwoda.evb.finance.analysis.application.service.CalculationService;
import com.sunwoda.evb.finance.analysis.application.service.CalculationServiceImpl;
import com.sunwoda.evb.finance.analysis.application.service.CalculationRetryApplicationService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionServiceImpl;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFact;
import com.sunwoda.evb.finance.analysis.domain.repository.AnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationJobRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcAnalysisBatchRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationResultRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationJobRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationRetryActionRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcCalculationRetryRepository;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcPnlFactProvider;
import com.sunwoda.evb.finance.analysis.infrastructure.jdbc.JdbcUserScopeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 失败处理 Worker 的新增接线配置。
 *
 * <p>本次修复：db profile 下显式接入计算服务及其最小 JDBC 依赖，修复 Worker 只扫描自身包
 * 导致 CalculationRetryExecutor 未创建的问题；同时保留只有 db profile 才启用的边界。</p>
 */
@Configuration
@Profile("db")
@EnableScheduling
@Import({JdbcCalculationJobRepository.class, JdbcCalculationRetryRepository.class,
        JdbcCalculationRetryActionRepository.class,
        // 本次修复：显式接入计算服务所需的批次、事实、结果和用户范围 JDBC 适配器。
        JdbcAnalysisBatchRepository.class, JdbcPnlFactProvider.class,
        JdbcCalculationResultRepository.class, JdbcUserScopeRepository.class,
        // 本次修复：显式导入应用层计算服务和权限服务，确保执行器可以实际创建。
        CalculationServiceImpl.class, PermissionServiceImpl.class})
public class CalculationRetryWorkerConfiguration {

    @Bean
    @Primary
    public CalculationRetryApplicationService calculationRetryApplicationService(
            CalculationJobRepository jobRepository,
            CalculationRetryRepository retryRepository,
            CalculationRetryActionRepository actionRepository,
            AnalysisBatchRepository batchRepository,
            PermissionService permissionService,
            CalculationRetryApplicationService.ScopeResolver scopeResolver) {
        // 本次修复：生产 Worker 必须使用带批次、权限和 scope 授权上下文的构造器。
        return new CalculationRetryApplicationService(jobRepository, retryRepository, actionRepository,
                batchRepository, permissionService, scopeResolver);
    }

    @Bean
    public CalculationRetryApplicationService.ScopeResolver calculationRetryScopeResolver(
            AnalysisBatchRepository batchRepository,
            PnlFactProvider factProvider) {
        // 本次修复：人工动作的 scope 来源于真实批次事实，而不是来自请求体或命令字段。
        return new CalculationRetryApplicationService.ScopeResolver() {
            @Override
            public Set<String> resolve(String batchNo) {
                if (batchNo == null || batchNo.trim().isEmpty()) {
                    throw new IllegalArgumentException("batchNo 不能为空");
                }
                AnalysisBatch batch = batchRepository.findByBatchNo(batchNo.trim())
                        .orElseThrow(() -> new IllegalArgumentException("批次不存在: " + batchNo));
                Set<String> scopeCodes = new LinkedHashSet<String>();
                for (PnlFact fact : factProvider.load(batch)) {
                    if (fact != null && fact.getScopeCode() != null
                            && !fact.getScopeCode().trim().isEmpty()) {
                        scopeCodes.add(fact.getScopeCode().trim());
                    }
                }
                return scopeCodes;
            }
        };
    }

    @Bean
    @ConditionalOnBean(CalculationService.class)
    public CalculationRetryExecutor calculationRetryExecutor(
            CalculationService calculationService,
            AuditEventPort auditEventPort,
            @Value("${analysis.worker.calculation-retry.operator:SYSTEM_RETRY_WORKER}")
            String operator) {
        // 本次修复：显式注入可配置内部身份和追加审计端口，身份未授权时由计算服务安全拒绝。
        return new CalculationServiceRetryExecutor(calculationService, auditEventPort, operator);
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
