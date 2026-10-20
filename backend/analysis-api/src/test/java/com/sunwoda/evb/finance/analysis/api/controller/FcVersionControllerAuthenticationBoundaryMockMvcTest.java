package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.FcVersionCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FC 版本写接口的认证边界 MockMvc 覆盖。 */
class FcVersionControllerAuthenticationBoundaryMockMvcTest {
    private static final String VERSION_CODE = "FC-2026-01";
    private static final String PERSPECTIVE = "BASE";
    private static final String BASE_PERIOD = "2026-01";

    private FcVersionCommandService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FcVersionCommandService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FcVersionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void rejectsCreateWhenPrincipalExistsButNameIsNull() throws Exception {
        // 认证主体对象存在但 getName() 为 null 时，必须在 FC 版本创建 Service 前拒绝请求。
        mockMvc.perform(post("/api/v1/fc-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionCode\":\"FC-2026-01\",\"perspective\":\"BASE\","
                                + "\"basePeriod\":\"2026-01\",\"coverageStart\":\"2026-01\","
                                + "\"coverageEnd\":\"2026-12\"}")
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsUploadWhenPrincipalExistsButNameIsNullEvenWithNonEmptyFile() throws Exception {
        // 文件非空不能绕过认证边界；getName() 为 null 时仍须在快照导入 Service 前拒绝请求。
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "snapshot.csv",
                "text/csv",
                "version_code,amount\nFC-2026-01,1\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(file)
                        .param("perspective", PERSPECTIVE)
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsPublishWhenPrincipalExistsButNameIsNull() throws Exception {
        // 认证主体对象存在但 getName() 为 null 时，必须在 FC 版本发布 Service 前拒绝请求。
        mockMvc.perform(post("/api/v1/fc-versions/{versionCode}/publish", VERSION_CODE)
                        .param("perspective", PERSPECTIVE)
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
