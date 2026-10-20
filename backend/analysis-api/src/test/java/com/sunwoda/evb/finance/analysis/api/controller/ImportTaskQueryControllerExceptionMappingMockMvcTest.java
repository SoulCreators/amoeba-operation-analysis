package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc coverage for multipart validation and the existing API exception envelope. */
class ImportTaskQueryControllerExceptionMappingMockMvcTest {
    private static final String TASK_NO = "TASK-001";
    private static final String USER_ID = "U001";

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
    void readsMultipartContentAndPassesInputStreamToService() throws Exception {
        byte[] content = "task-no,amount\nTASK-001,12\n".getBytes(StandardCharsets.UTF_8);
        ImportValidationResult result = new ImportValidationResult(
                TASK_NO, ImportStatus.VALID, 1,
                Collections.singletonList("amount"), Collections.emptyList());
        when(service.validate(eq(TASK_NO), any(InputStream.class))).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .file(new MockMultipartFile("file", "actual.csv", "text/csv", content))
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.rowCount").value(1));

        ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(service).validate(eq(TASK_NO), inputStreamCaptor.capture());
        assertArrayEquals(content, StreamUtils.copyToByteArray(inputStreamCaptor.getValue()));
    }

    @Test
    void mapsEmptyMultipartFileToOkAndA0001WithoutCallingService() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("导入文件不能为空"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullNamePrincipalForValidationWithUnauthorizedA0002() throws Exception {
        // 认证主体存在但 Principal.getName() 为 null 时，仍须在读取非空导入文件前拒绝请求。
        mockMvc.perform(multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                        .file(new MockMultipartFile("file", "actual.csv", "text/csv",
                                "amount,12\n".getBytes(StandardCharsets.UTF_8)))
                        .principal(principal(null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));

        verifyNoInteractions(service);
    }

    @Test
    void mapsIllegalArgumentFromValidationServiceToOkAndA0001() throws Exception {
        when(service.validate(eq(TASK_NO), any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("导入模板不匹配"));

        mockMvc.perform(validationRequest("actual.csv", "amount,12\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("导入模板不匹配"));

        verify(service).validate(eq(TASK_NO), any(InputStream.class));
    }

    @Test
    void mapsUnexpectedValidationFailureToOkAndA9999() throws Exception {
        when(service.validate(eq(TASK_NO), any(InputStream.class)))
                .thenThrow(new RuntimeException("unexpected validation failure"));

        mockMvc.perform(validationRequest("actual.csv", "amount,12\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).validate(eq(TASK_NO), any(InputStream.class));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validationRequest(
            String fileName, String content) {
        return multipart("/api/v1/imports/{taskNo}/validate", TASK_NO)
                .file(new MockMultipartFile("file", fileName, "text/csv",
                        content.getBytes(StandardCharsets.UTF_8)))
                .principal(principal(USER_ID));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
