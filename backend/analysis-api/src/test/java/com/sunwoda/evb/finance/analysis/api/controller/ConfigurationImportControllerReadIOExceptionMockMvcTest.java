package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 配置导入上传与校验读取异常的 HTTP 契约测试。 */
class ConfigurationImportControllerReadIOExceptionMockMvcTest {
    private static final String TASK_NO = "CFG-READ-001";

    private ConfigurationImportService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ConfigurationImportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConfigurationImportController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsUploadInputStreamIOExceptionToCurrentConfigReadEnvelope() throws Exception {
        // 本次修改：锁定配置文件 upload 读取失败时的现有 A0001 错误包络，并确认 Service 不被调用。
        mockMvc.perform(multipart("/api/v1/config/imports/{taskNo}/upload", TASK_NO)
                        .file(unreadableFile())
                        .principal(principal("finance-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置文件无法读取: read failed"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsValidateInputStreamIOExceptionToCurrentConfigReadEnvelope() throws Exception {
        // 本次修改：补齐配置文件 validate 读取失败分支，保持与 upload 相同的当前错误语义。
        mockMvc.perform(multipart("/api/v1/config/imports/{taskNo}/validate", TASK_NO)
                        .file(unreadableFile())
                        .principal(principal("finance-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置文件无法读取: read failed"));

        verifyNoInteractions(service);
    }

    private MockMultipartFile unreadableFile() {
        return new MockMultipartFile("file", "config.xlsx", "application/octet-stream", new byte[]{1}) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("read failed");
            }
        };
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
