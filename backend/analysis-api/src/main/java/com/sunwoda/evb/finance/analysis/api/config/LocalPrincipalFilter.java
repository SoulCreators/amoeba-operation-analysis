package com.sunwoda.evb.finance.analysis.api.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;

/**
 * 仅用于本地联调的认证主体适配器。生产环境必须由公司统一认证网关提供 Principal，
 * 不得启用此适配器作为真实权限控制。
 */
@Component
@Profile("local")
public class LocalPrincipalFilter extends OncePerRequestFilter {
    private static final String USER_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String userId = value(request.getHeader(USER_HEADER), "demo-finance");
        HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public Principal getUserPrincipal() {
                return new Principal() {
                    @Override
                    public String getName() { return userId; }
                };
            }
        };
        filterChain.doFilter(wrapped, response);
    }

    private String value(String candidate, String fallback) {
        return candidate == null || candidate.trim().isEmpty() ? fallback : candidate.trim();
    }
}
