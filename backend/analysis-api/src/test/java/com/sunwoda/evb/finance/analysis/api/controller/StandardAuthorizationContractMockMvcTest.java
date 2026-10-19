package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ExportAuthorizationException;
import com.sunwoda.evb.finance.analysis.application.service.ExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standard HTTP authentication/authorization contract tests.
 *
 * <p>The permission guard is deliberately test-local: the current repository
 * does not expose a production authorization interceptor yet. The test still
 * exercises the real export controller, real exception advice, and verifies
 * that rejected requests do not reach the business service.</p>
 */
class StandardAuthorizationContractMockMvcTest {
    private ExportService exportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        exportService = mock(ExportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ExportController(exportService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new ExplicitPermissionGuard())
                .build();
    }

    @Test
    void missingPrincipalReturns401WithCompatibleErrorBodyAndSkipsBusinessService() throws Exception {
        mockMvc.perform(get("/api/v1/exports/EXP-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(exportService);
    }

    @Test
    void explicitPermissionRejectionReturns403WithCompatibleErrorBodyAndSkipsBusinessService()
            throws Exception {
        mockMvc.perform(get("/api/v1/exports/EXP-001")
                        .principal(principal("denied-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("导出权限拒绝: 缺少导出权限"));

        verifyNoInteractions(exportService);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    /** Test-only stand-in for the future gateway/application permission guard. */
    private static final class ExplicitPermissionGuard implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                 Object handler) {
            Principal principal = request.getUserPrincipal();
            if (principal != null && "denied-user".equals(principal.getName())) {
                throw new ExportAuthorizationException("缺少导出权限");
            }
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response,
                               Object handler, ModelAndView modelAndView) {
            // No-op: this interceptor only models the pre-controller permission boundary.
        }
    }
}
