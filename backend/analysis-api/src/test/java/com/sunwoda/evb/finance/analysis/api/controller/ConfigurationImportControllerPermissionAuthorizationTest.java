package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 本次新增：用户权限模板只能由服务端识别出的启用 ADMIN 发起。 */
class ConfigurationImportControllerPermissionAuthorizationTest {
    private ConfigurationImportService service;
    private PermissionService permissions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ConfigurationImportService.class);
        permissions = mock(PermissionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ConfigurationImportController(service, permissions))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void rejectsNonAdminBeforeCreatingPermissionImportTask() throws Exception {
        when(permissions.isEnabledAdmin("U-1")).thenReturn(false);
        mvc.perform(post("/api/v1/config/imports").principal(() -> "U-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"USER_PERMISSION\",\"fileName\":\"permission.xlsx\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }
}
