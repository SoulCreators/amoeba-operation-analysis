package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
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

/** 导入任务上传读取异常的 HTTP 契约测试。 */
class ImportTaskQueryControllerReadIOExceptionMockMvcTest {
    private static final String TASK_NO = "TASK-READ-001";

    private ImportTaskService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ImportTaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ImportTaskQueryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void mapsInputStreamIOExceptionToCurrentImportReadEnvelope() throws Exception {
        // 本次修改：锁定文件 InputStream 读取失败时的现有 A0001 错误包络，并确认 Service 不被调用。
        mockMvc.perform(multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .file(unreadableFile())
                        .principal(principal("finance-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("导入文件无法读取: read failed"));

        verifyNoInteractions(service);
    }

    private MockMultipartFile unreadableFile() {
        return new MockMultipartFile("file", "actual.csv", "text/csv", new byte[]{1}) {
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
