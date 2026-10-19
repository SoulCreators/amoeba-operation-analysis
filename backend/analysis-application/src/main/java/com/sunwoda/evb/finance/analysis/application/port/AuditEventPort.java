package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;

/** Append-only application port for audit events. */
public interface AuditEventPort {
    void append(AuditEvent event);
}
