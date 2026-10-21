package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.FcSnapshotProvider;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcSnapshot;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

/** 按 FC 版本、批次期间和用户范围读取预测快照。 */
@Repository
@Profile("db")
public class JdbcFcSnapshotProvider implements FcSnapshotProvider {
    private final JdbcTemplate jdbc;

    public JdbcFcSnapshotProvider(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public List<FcSnapshot> findForBatch(String batchNo, AnalysisPerspective perspective,
                                         String versionCode, String userId) {
        String sql = "SELECT s.period, s.scope_code, s.metric_code, s.metric_value "
                + "FROM calc.fc_snapshot s "
                + "JOIN calc.fc_version v ON v.id = s.fc_version_id "
                + "JOIN meta.analysis_batch b ON b.period = s.period "
                + "WHERE b.batch_no = ? AND v.perspective = ? AND v.version_code = ? "
                + "AND EXISTS (SELECT 1 FROM iam.user_scope u "
                + "WHERE u.user_id = ? AND u.enabled = TRUE "
                // 本次修改：已启用 ADMIN 跨视角读取全部 FC 快照，普通角色继续按视角和 scope 过滤。
                + "AND (UPPER(u.role_code) = 'ADMIN' OR "
                + "(u.perspective = v.perspective AND (u.scope_code = 'ALL' OR u.scope_code = s.scope_code)))) "
                + "ORDER BY s.scope_code, s.metric_code";
        return jdbc.query(sql, (rs, row) -> new FcSnapshot(rs.getString("period"),
                rs.getString("scope_code"), rs.getString("metric_code"), rs.getBigDecimal("metric_value")),
                batchNo, perspective.getCode(), versionCode, userId);
    }
}
