package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.FcVersionCommandService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.FcVersion;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FC 版本 Controller 当前 HTTP 行为契约测试。
 * 本次修改只新增 MockMvc 测试，锁定现有成功包络、参数透传和异常映射，不定义正式治理规则。
 */
class FcVersionControllerContractMockMvcTest {
    private static final String VERSION_CODE = "FC-2026-01";
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
    void createReturnsCurrentSuccessEnvelopeAndPassesTrimmedPrincipalAndRequestFields() throws Exception {
        // 本次新增测试：验证创建成功包络、请求字段透传以及 Principal 用户名 trim 后传给 Service。
        FcVersion version = version("creator");
        when(service.create(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "creator")).thenReturn(version);

        mockMvc.perform(post("/api/v1/fc-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionCode\":\"FC-2026-01\",\"perspective\":\" BASE \""
                                + ",\"basePeriod\":\"2026-01\",\"coverageStart\":\"2026-01\","
                                + "\"coverageEnd\":\"2026-12\"}")
                        .principal(principal("  creator  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.versionCode").value(VERSION_CODE))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.basePeriod").value(BASE_PERIOD))
                .andExpect(jsonPath("$.data.coverageStart").value("2026-01"))
                .andExpect(jsonPath("$.data.coverageEnd").value("2026-12"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.uploadedBy").value("creator"))
                .andExpect(jsonPath("$.data.official").value(false));

        verify(service).create(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "creator");
    }

    @Test
    void uploadReturnsCurrentSuccessEnvelopeAndPassesTrimmedPerspectivePrincipalAndFile() throws Exception {
        // 本次新增测试：验证快照上传成功包络、视角与用户名 trim，以及 Multipart 文件内容传给 Service。
        ImportValidationResult result = new ImportValidationResult(
                "TASK-FC-001", ImportStatus.VALID, 1,
                Arrays.asList("version_code", "amount"), Collections.emptyList());
        when(service.uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.PRODUCT_LINE),
                eq(BASE_PERIOD), any(InputStream.class), eq("uploader"))).thenReturn(result);
        MockMultipartFile file = new MockMultipartFile("file", "snapshot.csv", "text/csv",
                "version_code,amount\nFC-2026-01,100\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(file)
                        .param("perspective", " PRODUCT_LINE ")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal("  uploader  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.taskNo").value("TASK-FC-001"))
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.rowCount").value(1))
                .andExpect(jsonPath("$.data.headers[0]").value("version_code"))
                .andExpect(jsonPath("$.data.headers[1]").value("amount"));

        ArgumentCaptor<InputStream> inputCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(service).uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.PRODUCT_LINE),
                eq(BASE_PERIOD), inputCaptor.capture(), eq("uploader"));
        assertStreamContent(inputCaptor.getValue(), "version_code,amount\nFC-2026-01,100\n");
    }

    @Test
    void publishReturnsCurrentSuccessEnvelopeAndPassesRequestParametersAndTrimmedPrincipal() throws Exception {
        // 本次新增测试：验证正式发布当前成功包络及路径、请求参数和 Principal 用户名的 Service 透传。
        FcVersion version = publishedVersion();
        when(service.publish(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD, "publisher"))
                .thenReturn(version);

        mockMvc.perform(post("/api/v1/fc-versions/{versionCode}/publish", VERSION_CODE)
                        .param("perspective", "BASE")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal(" publisher ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.versionCode").value(VERSION_CODE))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.official").value(true))
                .andExpect(jsonPath("$.data.publishedBy").value("publisher"))
                .andExpect(jsonPath("$.data.publishedAt").isArray())
                .andExpect(jsonPath("$.data.publishedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.publishedAt[1]").value(10))
                .andExpect(jsonPath("$.data.publishedAt[2]").value(20));

        verify(service).publish(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD, "publisher");
    }

    @Test
    void uploadWithEmptyFileUsesCurrentIllegalArgumentEnvelopeWithoutCallingService() throws Exception {
        // 本次新增测试：锁定空快照文件当前映射为 A0001，并确认空文件在 Service 前被拦截。
        MockMultipartFile emptyFile = new MockMultipartFile("file", "snapshot.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(emptyFile)
                        .param("perspective", "BASE")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal("uploader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("FC快照文件不能为空"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithUnsupportedPerspectiveUsesCurrentIllegalArgumentEnvelopeWithoutCallingService() throws Exception {
        // 本次新增测试：锁定非法 perspective 当前映射为 A0001，并确认参数解析失败时不调用 Service。
        mockMvc.perform(post("/api/v1/fc-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionCode\":\"FC-2026-01\",\"perspective\":\"UNKNOWN\","
                                + "\"basePeriod\":\"2026-01\",\"coverageStart\":\"2026-01\","
                                + "\"coverageEnd\":\"2026-12\"}")
                        .principal(principal("creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("不支持的数据视角: UNKNOWN"));

        verifyNoInteractions(service);
    }

    @Test
    void createServiceIllegalArgumentUsesCurrentErrorEnvelope() throws Exception {
        // 本次新增测试：锁定 Service IllegalArgumentException 的当前 A0001 包络和原始消息透传。
        when(service.create(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "creator"))
                .thenThrow(new IllegalArgumentException("FC版本已存在"));

        mockMvc.perform(post("/api/v1/fc-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionCode\":\"FC-2026-01\",\"perspective\":\"BASE\","
                                + "\"basePeriod\":\"2026-01\",\"coverageStart\":\"2026-01\","
                                + "\"coverageEnd\":\"2026-12\"}")
                        .principal(principal("creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("FC版本已存在"));

        verify(service).create(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "creator");
    }

    @Test
    void uploadServiceIllegalArgumentUsesCurrentErrorEnvelope() throws Exception {
        // 本次新增测试：锁定上传 Service IllegalArgumentException 的当前 A0001 包络和原始消息透传。
        when(service.uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.BASE), eq(BASE_PERIOD),
                any(InputStream.class), eq("uploader")))
                .thenThrow(new IllegalArgumentException("FC版本不存在"));

        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(snapshotFile())
                        .param("perspective", "BASE")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal("uploader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("FC版本不存在"));

        verify(service).uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.BASE), eq(BASE_PERIOD),
                any(InputStream.class), eq("uploader"));
    }

    @Test
    void uploadUnexpectedServiceFailureUsesCurrentGenericErrorEnvelope() throws Exception {
        // 本次新增测试：锁定上传 Service 未知异常的当前 A9999 通用包络，不暴露内部异常消息。
        when(service.uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.BASE), eq(BASE_PERIOD),
                any(InputStream.class), eq("uploader")))
                .thenThrow(new RuntimeException("unexpected upload failure"));

        mockMvc.perform(multipart("/api/v1/fc-versions/{versionCode}/snapshots", VERSION_CODE)
                        .file(snapshotFile())
                        .param("perspective", "BASE")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal("uploader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).uploadSnapshots(eq(VERSION_CODE), eq(AnalysisPerspective.BASE), eq(BASE_PERIOD),
                any(InputStream.class), eq("uploader"));
    }

    @Test
    void publishUnexpectedServiceFailureUsesCurrentGenericErrorEnvelope() throws Exception {
        // 本次新增测试：锁定 Service 未知异常的当前 A9999 通用包络，不把内部异常消息暴露给调用方。
        when(service.publish(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD, "publisher"))
                .thenThrow(new RuntimeException("unexpected publish failure"));

        mockMvc.perform(post("/api/v1/fc-versions/{versionCode}/publish", VERSION_CODE)
                        .param("perspective", "BASE")
                        .param("basePeriod", BASE_PERIOD)
                        .principal(principal("publisher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verify(service).publish(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD, "publisher");
    }

    private FcVersion version(String operator) {
        return new FcVersion(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "DRAFT", operator,
                LocalDateTime.of(2026, 10, 20, 10, 0), false, null, null);
    }

    private FcVersion publishedVersion() {
        // 本次新增夹具：为发布成功场景提供独立的已发布版本状态和发布元数据。
        return new FcVersion(VERSION_CODE, AnalysisPerspective.BASE, BASE_PERIOD,
                "2026-01", "2026-12", "PUBLISHED", "publisher",
                LocalDateTime.of(2026, 10, 20, 10, 0), true, "publisher",
                LocalDateTime.of(2026, 10, 20, 12, 30));
    }

    private MockMultipartFile snapshotFile() {
        // 本次新增辅助夹具：为上传 Service 异常映射测试提供统一的非空快照文件。
        return new MockMultipartFile("file", "snapshot.csv", "text/csv",
                "version_code,amount\nFC-2026-01,100\n".getBytes(StandardCharsets.UTF_8));
    }

    private Principal principal(String name) {
        return () -> name;
    }

    private void assertStreamContent(InputStream inputStream, String expected) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected,
                new String(output.toByteArray(), StandardCharsets.UTF_8));
    }
}
