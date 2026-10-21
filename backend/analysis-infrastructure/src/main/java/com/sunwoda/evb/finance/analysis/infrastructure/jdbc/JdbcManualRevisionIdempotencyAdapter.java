package com.sunwoda.evb.finance.analysis.infrastructure.jdbc;

import com.sunwoda.evb.finance.analysis.application.port.ManualRevisionIdempotencyPort;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * V030 人工修订请求的 PostgreSQL JDBC 持久幂等适配器。
 *
 * <p>本次新增：用数据库唯一键原子领取 requestKey；领取、人工修订操作和目标版本号持久化
 * 处于同一个 Spring 事务中。相同用户和 requestKey 的已完成请求只从数据库重放，不再次调用
 * 人工修订操作；不同请求载荷则显式失败，避免跨实例误复用结果。</p>
 */
@Repository
@Primary
@Profile("db")
public class JdbcManualRevisionIdempotencyAdapter implements ManualRevisionIdempotencyPort {
    private static final String CLAIM_SQL =
            "INSERT INTO flow.manual_revision_idempotency ("
                    + "user_id, request_key, source_calculation_version_id, revision_reason, "
                    + "request_payload_json, status) VALUES (?, ?, ?, ?, ?::jsonb, 'IN_PROGRESS') "
                    + "ON CONFLICT (user_id, request_key) DO NOTHING";

    private static final String EXISTING_SQL =
            "SELECT source_calculation_version_id, revision_reason, status, "
                    + "target_calculation_version_id FROM flow.manual_revision_idempotency "
                    + "WHERE user_id = ? AND request_key = ? FOR UPDATE";

    private static final String COMPLETE_SQL =
            "UPDATE flow.manual_revision_idempotency SET status = 'COMPLETED', "
                    + "target_calculation_version_id = ?, completed_at = CURRENT_TIMESTAMP "
                    + "WHERE user_id = ? AND request_key = ? AND status = 'IN_PROGRESS'";

    private final JdbcTemplate jdbc;
    private final CalculationVersionRepository versionRepository;

    // 本次修复：明确生产 DataSource 构造器，避免测试 JdbcTemplate 构造器导致 Spring 误找无参构造器。
    @Autowired
    public JdbcManualRevisionIdempotencyAdapter(DataSource dataSource,
                                                CalculationVersionRepository versionRepository) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource 不能为空");
        }
        if (versionRepository == null) {
            throw new IllegalArgumentException("versionRepository 不能为空");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.versionRepository = versionRepository;
    }

    // 本次新增：纯单测通过 JdbcTemplate 注入验证 SQL 分支，不改变生产构造器的 Bean 接线。
    JdbcManualRevisionIdempotencyAdapter(JdbcTemplate jdbc,
                                         CalculationVersionRepository versionRepository) {
        if (jdbc == null || versionRepository == null) {
            throw new IllegalArgumentException("幂等适配器依赖不能为空");
        }
        this.jdbc = jdbc;
        this.versionRepository = versionRepository;
    }

    /**
     * 本次新增：ON CONFLICT DO NOTHING 负责跨实例原子领取；命中已完成记录时锁定记录、校验载荷，
     * 再按持久化的 target_calculation_version_id 重放。人工修订操作抛错会回滚领取记录和全部结果。
     */
    @Override
    @Transactional
    public CalculationVersion execute(String userId,
                                      String requestKey,
                                      String sourceVersionId,
                                      String revisionReason,
                                      RevisionOperation operation) {
        String normalizedUserId = required(userId, "userId");
        String normalizedRequestKey = required(requestKey, "requestKey");
        String normalizedSourceVersionId = required(sourceVersionId, "sourceVersionId");
        String normalizedReason = required(revisionReason, "revisionReason");
        if (operation == null) {
            throw new IllegalArgumentException("operation 不能为空");
        }

        String requestPayload = requestPayload(normalizedUserId, normalizedRequestKey,
                normalizedSourceVersionId, normalizedReason);
        int claimed = jdbc.update(CLAIM_SQL, normalizedUserId, normalizedRequestKey,
                normalizedSourceVersionId, normalizedReason, requestPayload);
        if (claimed == 1) {
            CalculationVersion result = operation.execute();
            if (result == null || result.getCalculationVersionId() == null
                    || result.getCalculationVersionId().trim().isEmpty()) {
                throw new IllegalStateException("人工修订操作未返回目标计算版本");
            }
            int completed = jdbc.update(COMPLETE_SQL, result.getCalculationVersionId(),
                    normalizedUserId, normalizedRequestKey);
            if (completed != 1) {
                throw new IllegalStateException("人工修订幂等结果持久化失败");
            }
            return result;
        }

        List<IdempotencyRecordView> records = jdbc.query(EXISTING_SQL, RECORD_MAPPER,
                normalizedUserId, normalizedRequestKey);
        if (records.isEmpty()) {
            // 本次新增保护：唯一键冲突后若记录不可见，直接失败关闭，禁止退化为重复执行。
            throw new IllegalStateException("人工修订幂等记录不可见");
        }
        IdempotencyRecordView existing = records.get(0);
        if (!normalizedSourceVersionId.equals(existing.sourceVersionId)
                || !normalizedReason.equals(existing.revisionReason)) {
            throw new IllegalStateException("requestKey 已被不同人工修订请求占用");
        }
        if (!"COMPLETED".equals(existing.status)
                || existing.targetCalculationVersionId == null
                || existing.targetCalculationVersionId.trim().isEmpty()) {
            throw new IllegalStateException("人工修订请求正在执行或结果不完整");
        }
        return versionRepository.findById(existing.targetCalculationVersionId)
                .orElseThrow(() -> new IllegalStateException(
                        "人工修订幂等结果对应的计算版本不存在: "
                                + existing.targetCalculationVersionId));
    }

    private static final RowMapper<IdempotencyRecordView> RECORD_MAPPER =
            new RowMapper<IdempotencyRecordView>() {
                @Override
                public IdempotencyRecordView mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new IdempotencyRecordView(rs.getString("source_calculation_version_id"),
                            rs.getString("revision_reason"), rs.getString("status"),
                            rs.getString("target_calculation_version_id"));
                }
            };

    private static String requestPayload(String userId, String requestKey,
                                         String sourceVersionId, String revisionReason) {
        return "{\"userId\":\"" + jsonEscape(userId) + "\","
                + "\"requestKey\":\"" + jsonEscape(requestKey) + "\","
                + "\"sourceVersionId\":\"" + jsonEscape(sourceVersionId) + "\","
                + "\"revisionReason\":\"" + jsonEscape(revisionReason) + "\"}";
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '"') {
                escaped.append('\\');
            }
            if (ch == '\n') {
                escaped.append("\\n");
            } else if (ch == '\r') {
                escaped.append("\\r");
            } else if (ch == '\t') {
                escaped.append("\\t");
            } else {
                escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    // 本次新增：包级可见的查询投影只服务纯单测，生产数据仍由 PostgreSQL 行映射产生。
    static final class IdempotencyRecordView {
        private final String sourceVersionId;
        private final String revisionReason;
        private final String status;
        private final String targetCalculationVersionId;

        IdempotencyRecordView(String sourceVersionId, String revisionReason,
                              String status, String targetCalculationVersionId) {
            this.sourceVersionId = sourceVersionId;
            this.revisionReason = revisionReason;
            this.status = status;
            this.targetCalculationVersionId = targetCalculationVersionId;
        }
    }
}
