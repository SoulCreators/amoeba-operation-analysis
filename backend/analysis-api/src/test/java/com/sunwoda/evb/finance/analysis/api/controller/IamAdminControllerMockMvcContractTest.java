package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.IamAdminService;
import com.sunwoda.evb.finance.analysis.domain.model.IamUser;
import com.sunwoda.evb.finance.analysis.domain.model.UserAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本次新增：管理员 API 路由、统一响应、Principal 和布尔能力绑定契约。 */
class IamAdminControllerMockMvcContractTest {
    private IamAdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IamAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new IamAdminController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void getUsersUsesAdminPrincipalAndUnifiedEnvelope() throws Exception {
        when(service.listUsers("ADMIN-1", true)).thenReturn(Collections.singletonList(
                new IamUser("U-1", "用户一", null, null, false)));
        when(service.getUserAuthorization("ADMIN-1", "U-1")).thenReturn(
                new UserAuthorization("U-1", Collections.<String>emptyList(), Collections.emptyList()));

        mvc.perform(get("/api/v1/admin/users").param("includeDisabled", "true")
                        .principal(() -> " ADMIN-1 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].userId").value("U-1"))
                .andExpect(jsonPath("$.data[0].enabled").value(false));
        verify(service).listUsers("ADMIN-1", true);
    }

    @Test
    void putUserScopesBindsRolesPerspectiveAndBooleanCapabilities() throws Exception {
        when(service.replaceUserAuthorization(eq("ADMIN-1"), eq("U-1"), any(), any()))
                .thenReturn(new UserAuthorization("U-1", Collections.singletonList("BASE_FINANCE"),
                        Collections.emptyList()));
        when(service.getUser("ADMIN-1", "U-1")).thenReturn(new IamUser("U-1", "用户一", null, null, true));
        when(service.getUserAuthorization("ADMIN-1", "U-1")).thenReturn(
                new UserAuthorization("U-1", Collections.singletonList("BASE_FINANCE"), Collections.emptyList()));
        String body = "{\"roleCodes\":[\"BASE_FINANCE\"],\"scopes\":[{"
                + "\"roleCode\":\"BASE_FINANCE\",\"perspective\":\"BASE\",\"scopeCode\":\"BASE-A\","
                + "\"canViewDetail\":true,\"canExport\":true,\"canEditConfig\":false}]}";

        mvc.perform(put("/api/v1/admin/users/U-1/scopes").principal(() -> "ADMIN-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("U-1"));

        ArgumentCaptor<java.util.List> scopes = ArgumentCaptor.forClass(java.util.List.class);
        verify(service).replaceUserAuthorization(eq("ADMIN-1"), eq("U-1"),
                eq(Collections.singletonList("BASE_FINANCE")), scopes.capture());
        com.sunwoda.evb.finance.analysis.domain.model.UserScope grant =
                (com.sunwoda.evb.finance.analysis.domain.model.UserScope) scopes.getValue().get(0);
        assertEquals("BASE-A", grant.getScopeCode());
        assertEquals(true, grant.isCanViewDetail());
        assertEquals(true, grant.isCanExport());
        assertEquals(false, grant.isCanEditConfig());
    }

    @Test
    void anonymousWriteIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"U-1\",\"displayName\":\"用户一\",\"enabled\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"));
        verifyNoInteractions(service);
    }

    @Test
    void nonAdminServiceDenialMapsToForbidden() throws Exception {
        when(service.listUsers("U-1", false)).thenThrow(new IllegalStateException("仅ADMIN可管理IAM权限中心"));
        mvc.perform(get("/api/v1/admin/users").principal(() -> "U-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
