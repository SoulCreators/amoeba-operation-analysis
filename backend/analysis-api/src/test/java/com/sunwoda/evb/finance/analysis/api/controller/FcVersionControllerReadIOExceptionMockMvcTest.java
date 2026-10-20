package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.FcVersionCommandService;
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

/** FC 快照上传读取异常的 HTTP 契约测试。 */
class FcVersionControllerReadIOExceptionMockMvcTest {
    private static final String VERSION_CODE = "FC-READ-001";

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
    void mapsSnapshotInputStreamIOExceptionToCurrentFcReadEnvelope() throws Exception {
        // 本次修改：锁定 FC 快照读取失败时的现有 A0001 错误包络，并确认 Service 不被调用。
        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(unreadableFile())
                        .param("perspective", "BASE")
                        .param("basePeriod", "2026-01")
                        .principal(principal("finance-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("FC快照文件无法读取: read failed"));

        verifyNoInteractions(service);
    }

    private MockMultipartFile unreadableFile() {
        return new MockMultipartFile("file", "snapshot.csv", "text/csv", new byte[]{1}) {
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
