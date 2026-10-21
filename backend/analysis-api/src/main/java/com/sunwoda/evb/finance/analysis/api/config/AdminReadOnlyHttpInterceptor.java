package com.sunwoda.evb.finance.analysis.api.config;

import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;

/**
 * 生产 API 的 ADMIN HTTP 只读门禁。
 *
 * <p>ADMIN 的全量读取授权仍由应用权限服务负责；本拦截器只阻断
 * ADMIN 的 HTTP 写方法，防止遗漏某个写 Controller 时把只读角色升级为写权限。</p>
 */
@Component
@Profile("db")
public class AdminReadOnlyHttpInterceptor implements HandlerInterceptor {
    private final PermissionService permissionService;

    public AdminReadOnlyHttpInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (isReadMethod(request.getMethod())) return true;

        Principal principal = request.getUserPrincipal();
        if (principal == null || isBlank(principal.getName())) {
            // 本次新增：匿名请求继续交给 Controller/认证链返回 401，不在只读门禁中吞掉认证语义。
            return true;
        }
        if (permissionService.isEnabledAdmin(principal.getName().trim())) {
            // 本次新增：ADMIN 只允许安全读取方法，所有 API 写方法统一返回 403。
            throw new IllegalStateException("ADMIN 只读权限禁止 HTTP 写请求");
        }
        return true;
    }

    private boolean isReadMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
