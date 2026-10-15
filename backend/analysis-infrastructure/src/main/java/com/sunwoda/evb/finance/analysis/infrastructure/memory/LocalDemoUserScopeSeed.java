package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** Local-only seed; production IAM data is never changed by this component. */
@Component
@Profile("local")
public class LocalDemoUserScopeSeed {
    private final UserScopeRepository repository;

    public LocalDemoUserScopeSeed(UserScopeRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void seed() {
        for (AnalysisPerspective perspective : AnalysisPerspective.values()) {
            repository.save(new UserScope("demo-finance", "FINANCE_ADMIN", perspective,
                    "ALL", true, true, true, true));
        }
    }
}
