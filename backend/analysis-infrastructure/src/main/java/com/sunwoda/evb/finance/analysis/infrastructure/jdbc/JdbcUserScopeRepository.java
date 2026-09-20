package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import com.sunwoda.evb.finance.analysis.domain.repository.UserScopeRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
@Primary
@Profile("db")
public class JdbcUserScopeRepository implements UserScopeRepository {
    private final JdbcTemplate jdbc;

    public JdbcUserScopeRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }

    @Override
    public UserScope save(UserScope scope) {
        jdbc.update("INSERT INTO iam.user_scope(user_id, role_code, perspective, scope_code, can_view_detail, "
                        + "can_export, can_edit_config, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id, role_code, perspective, scope_code) DO UPDATE SET "
                        + "can_view_detail = EXCLUDED.can_view_detail, can_export = EXCLUDED.can_export, "
                        + "can_edit_config = EXCLUDED.can_edit_config, enabled = EXCLUDED.enabled, updated_at = CURRENT_TIMESTAMP",
                scope.getUserId(), scope.getRoleCode(), scope.getPerspective().getCode(), scope.getScopeCode(),
                scope.isCanViewDetail(), scope.isCanExport(), scope.isCanEditConfig(), scope.isEnabled());
        return scope;
    }

    @Override
    public List<UserScope> findByUserAndPerspective(String userId, AnalysisPerspective perspective) {
        return jdbc.query("SELECT user_id, role_code, perspective, scope_code, can_view_detail, can_export, "
                        + "can_edit_config, enabled FROM iam.user_scope WHERE user_id = ? AND perspective = ? AND enabled = TRUE "
                        + "ORDER BY scope_code", (rs, row) -> new UserScope(rs.getString("user_id"),
                        rs.getString("role_code"), AnalysisPerspective.fromCode(rs.getString("perspective")),
                        rs.getString("scope_code"), rs.getBoolean("can_view_detail"), rs.getBoolean("can_export"),
                        rs.getBoolean("can_edit_config"), rs.getBoolean("enabled")), userId, perspective.getCode());
    }
}
