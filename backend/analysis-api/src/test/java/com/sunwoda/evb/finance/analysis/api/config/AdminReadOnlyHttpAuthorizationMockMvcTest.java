package com.sunwoda.evb.finance.analysis.api.config;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.security.Principal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ADMIN HTTP 只读边界回归。
 *
 * <p>使用真实拦截器和真实异常处理器，探针 Controller 仅用于证明拒绝发生在
 * 业务 Controller 之前；不改变任何业务 Service 或权限数据。</p>
 */
class AdminReadOnlyHttpAuthorizationMockMvcTest {
    private static final String ADMIN_USER = "IT-ADMIN-001";
    private static final String NORMAL_USER = "FINANCE-001";

    private PermissionService permissionService;
    private ProbeController probeController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        permissionService = mock(PermissionService.class);
        probeController = new ProbeController();
        AdminReadOnlyHttpInterceptor interceptor = new AdminReadOnlyHttpInterceptor(permissionService);
        mockMvc = MockMvcBuilders.standaloneSetup(probeController)
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    @Test
    void enabledAdminCannotReachAnyHttpWriteMethod() throws Exception {
        when(permissionService.isEnabledAdmin(ADMIN_USER)).thenReturn(true);

        assertWriteRejected(post("/api/v1/probe"));
        assertWriteRejected(put("/api/v1/probe"));
        assertWriteRejected(patch("/api/v1/probe"));
        assertWriteRejected(delete("/api/v1/probe"));

        assertEquals(0, probeController.invocations.get());
        verify(permissionService, org.mockito.Mockito.times(4)).isEnabledAdmin(ADMIN_USER);
    }

    @Test
    void enabledAdminCanUseSafeReadMethods() throws Exception {
        when(permissionService.isEnabledAdmin(ADMIN_USER)).thenReturn(true);

        mockMvc.perform(get("/api/v1/probe").principal(principal(ADMIN_USER)))
                .andExpect(status().isOk());
        mockMvc.perform(head("/api/v1/probe").principal(principal(ADMIN_USER)))
                .andExpect(status().isOk());
        mockMvc.perform(options("/api/v1/probe").principal(principal(ADMIN_USER)))
                .andExpect(status().isOk());

        assertEquals(2, probeController.invocations.get());
        verify(permissionService, org.mockito.Mockito.never()).isEnabledAdmin(ADMIN_USER);
    }

    @Test
    void enabledAdminCanReachOnlyDedicatedIamManagementWritePath() throws Exception {
        when(permissionService.isEnabledAdmin(ADMIN_USER)).thenReturn(true);

        mockMvc.perform(post("/api/v1/admin/users").principal(principal(ADMIN_USER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/probe").principal(principal(ADMIN_USER)))
                .andExpect(status().isForbidden());

        // 本次新增：管理员路径不由全局只读门禁拦截，真实 Controller 仍需在应用服务内校验 ADMIN。
        assertEquals(0, probeController.invocations.get());
        verify(permissionService).isEnabledAdmin(ADMIN_USER);
    }

    @Test
    void nonAdminAndDisabledAdminAreNotBlockedAndClientRoleIsIgnored() throws Exception {
        when(permissionService.isEnabledAdmin(NORMAL_USER)).thenReturn(false);
        when(permissionService.isEnabledAdmin("DISABLED-ADMIN")).thenReturn(false);

        mockMvc.perform(post("/api/v1/probe")
                        .principal(principal(NORMAL_USER))
                        .header("X-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/probe")
                        .principal(principal("DISABLED-ADMIN")))
                .andExpect(status().isOk());

        assertEquals(2, probeController.invocations.get());
        verify(permissionService).isEnabledAdmin(NORMAL_USER);
        verify(permissionService).isEnabledAdmin("DISABLED-ADMIN");
    }

    @Test
    void anonymousWriteIsLeftForTheExistingAuthenticationBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/probe"))
                .andExpect(status().isOk());

        assertEquals(1, probeController.invocations.get());
        verifyNoInteractions(permissionService);
    }

    @Test
    void productionConfigurationRegistersTheGuardOnlyForApiPaths() {
        AdminReadOnlyHttpInterceptor interceptor = new AdminReadOnlyHttpInterceptor(permissionService);
        RecordingInterceptorRegistry registry = new RecordingInterceptorRegistry();

        new AdminReadOnlyWebMvcConfiguration(interceptor).addInterceptors(registry);

        assertEquals(1, registry.registered().size());
        MappedInterceptor mapped = (MappedInterceptor) registry.registered().get(0);
        assertSame(interceptor, mapped.getInterceptor());
        assertEquals("/api/v1/**", mapped.getPathPatterns()[0]);
    }

    private void assertWriteRejected(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.principal(principal(ADMIN_USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("ADMIN 只读权限禁止 HTTP 写请求"));
    }

    private Principal principal(String name) {
        return () -> name;
    }

    /** 暴露 Spring 受保护注册列表，验证生产 WebMvc 配置确实完成路径绑定。 */
    private static final class RecordingInterceptorRegistry extends InterceptorRegistry {
        private java.util.List<Object> registered() {
            return getInterceptors();
        }
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    private static final class ProbeController {
        private final AtomicInteger invocations = new AtomicInteger();

        @GetMapping
        @ResponseBody
        public String get() {
            invocations.incrementAndGet();
            return "read";
        }

        @PostMapping
        @ResponseBody
        public String post() {
            invocations.incrementAndGet();
            return "write";
        }

        @PutMapping
        @ResponseBody
        public String put() {
            invocations.incrementAndGet();
            return "write";
        }

        @PatchMapping
        @ResponseBody
        public String patch() {
            invocations.incrementAndGet();
            return "write";
        }

        @DeleteMapping
        @ResponseBody
        public String delete() {
            invocations.incrementAndGet();
            return "write";
        }
    }
}
