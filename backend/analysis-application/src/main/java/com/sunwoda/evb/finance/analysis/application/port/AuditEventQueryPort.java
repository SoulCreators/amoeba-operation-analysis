package com.sunwoda.evb.finance.analysis.application.port;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;

import java.util.List;

/**
 * 审计事件只读查询端口。
 *
 * <p>本次新增：将查询能力与既有追加写端口分离；实现只能根据查询条件
 * 读取事件，不需要也不应通过本端口追加、修改或删除事件。</p>
 */
public interface AuditEventQueryPort {
    List<AuditEvent> find(AuditEventQuery query);
}
