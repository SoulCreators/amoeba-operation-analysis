package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.IamRole;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.IamManagementRepository;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** Local-only seed; production IAM data is never changed by this component. */
@Component
@Profile("local")
public class LocalDemoUserScopeSeed {
    private final UserScopeRepository repository;
    private final IamManagementRepository iamRepository;

    public LocalDemoUserScopeSeed(UserScopeRepository repository,
                                  IamManagementRepository iamRepository) {
        this.repository = repository;
        this.iamRepository = iamRepository;
    }

    @PostConstruct
    public void seed() {
        seedIamMasterData();
        for (AnalysisPerspective perspective : AnalysisPerspective.values()) {
            repository.save(new UserScope("local-finance", "FINANCE_ADMIN", perspective,
                    "ALL", true, true, true, true));
        }
    }

    /** 本次新增：本地默认主体可直接进入 IAM 管理页；生产环境不会加载 local Profile。 */
    private void seedIamMasterData() {
        if (iamRepository.findRole("ADMIN") == null) {
            iamRepository.insertRole(new IamRole("ADMIN", "系统管理员",
                    "本地联调 IAM 管理角色", true, true), "LOCAL_SEED");
        }
        if (iamRepository.findRole("FINANCE_ADMIN") == null) {
            iamRepository.insertRole(new IamRole("FINANCE_ADMIN", "总部财经",
                    "本地联调最大经营权限角色", true, true), "LOCAL_SEED");
        }
        if (iamRepository.findUser("local-finance") == null) {
            iamRepository.insertUser(new IamUser("local-finance", "local-finance", "local-finance",
                    "本地财经管理员", null, "本地联调", true), "LOCAL_SEED");
        }
        iamRepository.replaceUserRoles("local-finance",
                java.util.Arrays.asList("ADMIN", "FINANCE_ADMIN"), "LOCAL_SEED");
    }
}
