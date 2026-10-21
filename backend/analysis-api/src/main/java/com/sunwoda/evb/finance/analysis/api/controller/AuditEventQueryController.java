package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.model.AuditEvent;
import com.sunwoda.evb.finance.analysis.application.model.AuditEventQuery;
import com.sunwoda.evb.finance.analysis.application.service.AuditEventQueryService;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 审计事件只读查询 HTTP 入口。
 *
 * <p>本次新增：请求身份只从认证 Principal 获取，查询参数中的 userId、role
 * 即使存在也不会参与授权或构造查询条件。</p>
 */
@RestController
@Profile("db")
@RequestMapping("/api/v1/audit-logs")
public class AuditEventQueryController {
    private final AuditEventQueryService queryService;

    // 本次新增：通过构造器注入应用层查询服务，保持 HTTP 层不直接接触查询端口或权限数据。
    public AuditEventQueryController(AuditEventQueryService queryService) {
        if (queryService == null) throw new IllegalArgumentException("queryService must not be null");
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResult<List<AuditEvent>> query(
            @RequestParam(value = "eventId", required = false) String eventId,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "operator", required = false) String operator,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "objectType", required = false) String objectType,
            @RequestParam(value = "objectId", required = false) String objectId,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "occurredFrom", required = false) String occurredFrom,
            @RequestParam(value = "occurredTo", required = false) String occurredTo,
            Principal principal) {
        // 本次新增：先校验认证身份，保证匿名请求不会因过滤参数而进入应用服务。
        String userId = requiredUser(principal);
        AuditEventQuery query = new AuditEventQuery(eventId, traceId, operator, service, action,
                objectType, objectId, result, parseDateTime(occurredFrom, "occurredFrom"),
                parseDateTime(occurredTo, "occurredTo"));
        return ApiResult.ok(queryService.query(userId, query));
    }

    private String requiredUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("未获取到认证用户");
        }
        return principal.getName().trim();
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            // 本次新增：把非法过滤时间转换为统一参数错误，避免向下游传递未解析字符串。
            throw new IllegalArgumentException(fieldName + " 必须为 ISO-8601 本地日期时间");
        }
    }
}
