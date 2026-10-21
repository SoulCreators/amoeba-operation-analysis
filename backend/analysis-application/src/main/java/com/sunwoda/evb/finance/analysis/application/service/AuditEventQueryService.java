package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventQueryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 审计事件应用层查询服务。
 *
 * <p>本次新增：在访问查询端口前执行只读授权；ADMIN 查询不做业务范围
 * 裁剪，返回端口按条件提供的全量结果。其他角色当前默认拒绝，因为
 * 现有 AuditEvent/AuditEventPort 契约没有审计范围字段或审计读取动作。</p>
 */
@Service
@Profile("db")
public final class AuditEventQueryService {
    private final AuditEventReadPolicy readPolicy;
    private final AuditEventQueryPort queryPort;

    // 本次新增：查询端口与追加写端口分离，保证既有 AuditEventPort 调用方无需改动。
    public AuditEventQueryService(AuditEventReadPolicy readPolicy,
                                  AuditEventQueryPort queryPort) {
        if (readPolicy == null) {
            throw new IllegalArgumentException("readPolicy must not be null");
        }
        if (queryPort == null) {
            throw new IllegalArgumentException("queryPort must not be null");
        }
        this.readPolicy = readPolicy;
        this.queryPort = queryPort;
    }

    /** 本次新增：只允许启用 ADMIN 查询，并冻结端口返回集合避免应用层二次修改。 */
    public List<AuditEvent> query(String userId, AuditEventQuery query) {
        String normalizedUserId = required(userId, "userId");
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (!readPolicy.canReadAll(normalizedUserId)) {
            throw new IllegalStateException("当前用户无审计查询权限");
        }

        List<AuditEvent> events = queryPort.find(query);
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<AuditEvent>(events));
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
