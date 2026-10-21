package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.CalculationVersionScopeResolver;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PostgreSQL 计算版本实际 scope 解析器。
 *
 * <p>本次新增：人工修订授权必须以来源版本的结果头为证据，直接按
 * {@code calc.pnl_result.calculation_version_id} 读取实际 {@code scope_code}；
 * 旧版本没有结果明细时失败关闭，禁止用伪造的 {@code ALL} 放行。</p>
 */
@Component
@Profile("db")
public class JdbcCalculationVersionScopeResolver implements CalculationVersionScopeResolver {
    private static final String SCOPE_SQL =
            "SELECT DISTINCT p.scope_code "
                    + "FROM calc.pnl_result p "
                    + "WHERE p.calculation_version_id = ? "
                    + "ORDER BY p.scope_code";

    private final JdbcTemplate jdbc;

    @Autowired
    public JdbcCalculationVersionScopeResolver(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // 本次新增：包内构造器只供契约测试注入 JdbcTemplate，不改变生产 Spring 接线。
    JdbcCalculationVersionScopeResolver(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbc 不能为空");
        }
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> resolve(CalculationVersion sourceVersion) {
        if (sourceVersion == null) {
            throw new IllegalArgumentException("来源计算版本不能为空");
        }

        List<String> rows = jdbc.query(SCOPE_SQL,
                (resultSet, rowNum) -> resultSet.getString("scope_code"),
                sourceVersion.getCalculationVersionId());
        if (rows.isEmpty()) {
            // 本次新增：历史遗留版本没有结果明细时必须拒绝授权，不能降级为 ALL。
            throw new IllegalStateException("来源计算版本没有可审计的结果范围: "
                    + sourceVersion.getCalculationVersionId());
        }

        Set<String> scopeCodes = new LinkedHashSet<String>();
        for (String scopeCode : rows) {
            if (scopeCode == null || scopeCode.trim().isEmpty()) {
                // 本次新增：结果范围缺失同样失败关闭，避免空值被权限层误解释为全量。
                throw new IllegalStateException("来源计算版本包含空的结果范围: "
                        + sourceVersion.getCalculationVersionId());
            }
            scopeCodes.add(scopeCode);
        }
        if (scopeCodes.isEmpty()) {
            throw new IllegalStateException("来源计算版本没有可审计的结果范围: "
                    + sourceVersion.getCalculationVersionId());
        }
        return Collections.unmodifiableSet(scopeCodes);
    }
}
