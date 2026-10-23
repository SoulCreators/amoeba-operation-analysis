package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.NonfinancialPublication;
import com.sunwoda.evb.finance.analysis.domain.repository.NonfinancialPublicationRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 非财经指标发布记录内存仓储，用于隔离切片与测试。 */
@Repository
public class InMemoryNonfinancialPublicationRepository implements NonfinancialPublicationRepository {
    private final Map<String, NonfinancialPublication> byPeriod =
            new LinkedHashMap<String, NonfinancialPublication>();

    @Override
    public synchronized NonfinancialPublication save(NonfinancialPublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("publication 不能为空");
        }
        byPeriod.put(publication.getPeriod(), publication);
        return publication;
    }

    @Override
    public synchronized Optional<NonfinancialPublication> findByPeriod(String period) {
        return Optional.ofNullable(byPeriod.get(period));
    }
}
