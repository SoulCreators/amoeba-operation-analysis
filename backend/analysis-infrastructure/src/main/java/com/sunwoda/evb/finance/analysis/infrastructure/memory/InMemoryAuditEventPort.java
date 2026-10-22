package com.sunwoda.evb.finance.analysis.infrastructure.memory;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.port.AuditEventPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 本次新增：非 db Profile 下保留 IAM 写入审计，供本地联调和单元验证。 */
@Repository
@Profile("!db")
public class InMemoryAuditEventPort implements AuditEventPort {
    private final List<AuditEvent> events = new ArrayList<AuditEvent>();

    @Override public synchronized void append(AuditEvent event) { events.add(event); }
    public synchronized List<AuditEvent> findAll() {
        return Collections.unmodifiableList(new ArrayList<AuditEvent>(events));
    }
}
