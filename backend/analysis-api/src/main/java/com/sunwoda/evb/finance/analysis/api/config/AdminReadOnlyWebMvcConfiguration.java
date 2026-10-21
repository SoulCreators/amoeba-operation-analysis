package com.sunwoda.evb.finance.analysis.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 生产 API 的 ADMIN 只读拦截器注册配置。
 *
 * <p>只匹配业务 API 路径，不拦截 Actuator 健康检查和静态资源；仅在
 * {@code db} Profile 注册，避免本地联调认证适配器被误认为生产认证。</p>
 */
@Configuration
@Profile("db")
public class AdminReadOnlyWebMvcConfiguration implements WebMvcConfigurer {
    private final AdminReadOnlyHttpInterceptor interceptor;

    public AdminReadOnlyWebMvcConfiguration(AdminReadOnlyHttpInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 本次新增：所有 API 入口统一经过 ADMIN HTTP 写权限拒绝门禁。
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
