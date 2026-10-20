package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
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

/** MockMvc contract coverage for configuration import multipart endpoints. */
class ConfigurationImportControllerMultipartContractTest {
    private static final String TASK_NO = "CFG-TASK-001";
    private static final String USER_ID = "U001";

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
    void validateRejectsEmptyMultipartFileWithoutCallingService() throws Exception {
        mockMvc.perform(validationRequest(principal(USER_ID), new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置文件不能为空"));

        verifyNoInteractions(service);
    }

    @Test
    void uploadRejectsEmptyMultipartFileWithoutCallingService() throws Exception {
        mockMvc.perform(uploadRequest(principal(USER_ID), new byte[0]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置文件不能为空"));

        verifyNoInteractions(service);
    }

    @Test
    void validateAndUploadRejectMissingOrBlankPrincipalBeforeCallingService() throws Exception {
        assertUnauthenticated(validationRequest(null, bytes("type,code\nFX_RATE,CNY\n")));
        assertUnauthenticated(validationRequest(principal(""), bytes("type,code\nFX_RATE,CNY\n")));
        assertUnauthenticated(validationRequest(principal("   "), bytes("type,code\nFX_RATE,CNY\n")));
        assertUnauthenticated(uploadRequest(null, bytes("type,code\nFX_RATE,CNY\n")));
        assertUnauthenticated(uploadRequest(principal(""), bytes("type,code\nFX_RATE,CNY\n")));
        assertUnauthenticated(uploadRequest(principal("   "), bytes("type,code\nFX_RATE,CNY\n")));

        verifyNoInteractions(service);
    }

    @Test
    void validateDelegatesMultipartContentAndReturnsSuccessfulEnvelope() throws Exception {
        byte[] content = bytes("type,code\nFX_RATE,CNY\n");
        ImportValidationResult result = new ImportValidationResult(
                TASK_NO, ImportStatus.VALID, 1,
                Collections.singletonList("type"), Collections.emptyList());
        when(service.validate(eq(TASK_NO), any(InputStream.class))).thenReturn(result);

        mockMvc.perform(validationRequest(principal(USER_ID), content))
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
    void uploadDelegatesMultipartContentAndReturnsSuccessfulEnvelope() throws Exception {
        byte[] content = bytes("type,code\nFX_RATE,CNY\n");
        when(service.upload(eq(TASK_NO), any(InputStream.class))).thenReturn(task());

        mockMvc.perform(uploadRequest(principal(USER_ID), content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskNo").value(TASK_NO))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        ArgumentCaptor<InputStream> inputStreamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(service).upload(eq(TASK_NO), inputStreamCaptor.capture());
        assertArrayEquals(content, StreamUtils.copyToByteArray(inputStreamCaptor.getValue()));
    }

    @Test
    void validateMapsIllegalArgumentToA0001Envelope() throws Exception {
        when(service.validate(eq(TASK_NO), any(InputStream.class)))
                .thenThrow(new IllegalArgumentException("配置模板不匹配"));

        mockMvc.perform(validationRequest(principal(USER_ID), bytes("invalid\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("配置模板不匹配"));

        verify(service).validate(eq(TASK_NO), any(InputStream.class));
    }

    @Test
    void uploadMapsUnexpectedFailureToA9999Envelope() throws Exception {
        when(service.upload(eq(TASK_NO), any(InputStream.class)))
                .thenThrow(new RuntimeException("unexpected upload failure"));

        mockMvc.perform(uploadRequest(principal(USER_ID), bytes("type,code\nFX_RATE,CNY\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).upload(eq(TASK_NO), any(InputStream.class));
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));
    }

    private MockHttpServletRequestBuilder validationRequest(Principal principal, byte[] content) {
        MockHttpServletRequestBuilder request = multipart("/api/v1/config/imports/{taskNo}/validate", TASK_NO)
                .file(new MockMultipartFile("file", "config.xlsx", "application/octet-stream", content));
        return withPrincipal(request, principal);
    }

    private MockHttpServletRequestBuilder uploadRequest(Principal principal, byte[] content) {
        MockHttpServletRequestBuilder request = multipart("/api/v1/config/imports/{taskNo}/upload", TASK_NO)
                .file(new MockMultipartFile("file", "config.xlsx", "application/octet-stream", content));
        return withPrincipal(request, principal);
    }

    private MockHttpServletRequestBuilder withPrincipal(MockHttpServletRequestBuilder request,
                                                         Principal principal) {
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private ConfigurationImportTask task() {
        return new ConfigurationImportTask(1L, TASK_NO, ConfigurationImportType.FX_RATE,
                "config.xlsx", "sha256", USER_ID,
                LocalDateTime.of(2026, 10, 20, 10, 0));
    }
}
