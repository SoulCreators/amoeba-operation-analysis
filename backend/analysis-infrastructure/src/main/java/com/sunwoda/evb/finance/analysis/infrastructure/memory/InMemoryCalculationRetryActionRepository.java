package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.CalculationRetryAction;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationRetryActionRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 人工动作内存仓储。
 *
 * <p>本次新增：独立保存每次人工重试/补偿动作，供应用测试验证审计字段不被覆盖。</p>
 * <p>本次补充：默认内存上下文直接启用该仓储，避免失败处理应用服务缺少动作端口。</p>
 */
@Repository
public class InMemoryCalculationRetryActionRepository implements CalculationRetryActionRepository {
    private final List<CalculationRetryAction> actions = new ArrayList<CalculationRetryAction>();

    @Override
    public synchronized CalculationRetryAction save(CalculationRetryAction action) {
        if (action == null) throw new IllegalArgumentException("action 不能为空");
        actions.add(action);
        return action;
    }

    @Override
    public synchronized List<CalculationRetryAction> findByJobId(String jobId) {
        List<CalculationRetryAction> result = new ArrayList<CalculationRetryAction>();
        for (CalculationRetryAction action : actions) {
            if (action.getJobId().equals(jobId)) result.add(action);
        }
        return Collections.unmodifiableList(result);
    }
}
