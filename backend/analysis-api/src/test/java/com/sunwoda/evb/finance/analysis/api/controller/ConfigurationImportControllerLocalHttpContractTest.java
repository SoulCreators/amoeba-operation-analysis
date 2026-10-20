package com.sunwoda.evb.finance.analysis.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunwoda.evb.finance.analysis.api.AnalysisApiApplication;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportTask;
import com.sunwoda.evb.finance.analysis.domain.model.ConfigurationImportType;
import com.sunwoda.evb.finance.analysis.domain.model.ImportStatus;
import com.sunwoda.evb.finance.analysis.domain.model.ImportValidationResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * FE-CONFIG-IMPORT-MULTIPART-001 的本地真实 HTTP 契约测试。
 * <p>
 * 本测试启动真实 Spring Boot Web 容器和随机端口，只用 MockBean 隔离业务服务的持久化与文件副作用；
 * 因而覆盖请求路由、Servlet multipart 解析、@RequestPart("file") 绑定和 JSON 返回封装，
 * 不对上传文件格式作任何冻结，也不把 local 的认证主体适配器当作授权校验。
 */
@SpringBootTest(
        classes = AnalysisApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.port=0")
@ActiveProfiles("local")
class ConfigurationImportControllerLocalHttpContractTest {
    private static final String TASK_NO = "CFG-HTTP-001";
    private static final byte[] FILE_BYTES = new byte[]{0, 1, 2, 10, 13, 127};

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @MockBean
    private ConfigurationImportService service;

    @Test
    void localProfileHealthEndpointIsReachableThroughRandomPortHttpServer() throws Exception {
        // 这是独立的 local profile 启动与真实 HTTP 可达性前置检查，不作为 multipart 契约通过依据。
        assertThat(Arrays.asList(environment.getActiveProfiles())).contains("local");

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()).path("status").asText()).isEqualTo("UP");
    }

    @Test
    void uploadBindsFilePartThroughRealMvcAndReturnsSuccessEnvelope() throws Exception {
        ConfigurationImportTask task = task(ImportStatus.VALIDATING);
        when(service.upload(eq(TASK_NO), any(InputStream.class))).thenReturn(task);
        ArgumentCaptor<InputStream> inputCaptor = ArgumentCaptor.forClass(InputStream.class);

        ResponseEntity<String> response = postMultipart("/api/v1/config/imports/" + TASK_NO + "/upload");

        assertSuccessEnvelope(response, "VALIDATING");
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("taskNo").asText()).isEqualTo(TASK_NO);
        assertThat(data.path("type").asText()).isEqualTo("FX_RATE");
        verify(service).upload(eq(TASK_NO), inputCaptor.capture());
        assertThat(readAll(inputCaptor.getValue())).containsExactly(FILE_BYTES);
        verifyNoMoreInteractions(service);
    }

    @Test
    void validateBindsFilePartThroughRealMvcAndReturnsSuccessEnvelope() throws Exception {
        ImportValidationResult result = new ImportValidationResult(
                TASK_NO, ImportStatus.VALID, 1, Collections.singletonList("opaque-column"), Collections.emptyList());
        when(service.validate(eq(TASK_NO), any(InputStream.class))).thenReturn(result);
        ArgumentCaptor<InputStream> inputCaptor = ArgumentCaptor.forClass(InputStream.class);

        ResponseEntity<String> response = postMultipart("/api/v1/config/imports/" + TASK_NO + "/validate");

        assertSuccessEnvelope(response, "VALID");
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        assertThat(data.path("taskNo").asText()).isEqualTo(TASK_NO);
        assertThat(data.path("rowCount").asInt()).isEqualTo(1);
        assertThat(data.path("headers").get(0).asText()).isEqualTo("opaque-column");
        verify(service).validate(eq(TASK_NO), inputCaptor.capture());
        assertThat(readAll(inputCaptor.getValue())).containsExactly(FILE_BYTES);
        verifyNoMoreInteractions(service);
    }

    @Test
    void nonFilePartDoesNotBindAndStillUsesApiErrorEnvelope() throws Exception {
        // 以错误 part 名称请求，验证契约固定的是 file，而不是任意 multipart 字段。
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("other", new NamedByteArrayResource(FILE_BYTES, "contract-input.bin"));
        ResponseEntity<String> response = postMultipart(body, "/api/v1/config/imports/" + TASK_NO + "/upload");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("code").asText()).isEqualTo("A9999");
        verifyNoMoreInteractions(service);
    }

    @Test
    void validateNonFilePartDoesNotBindAndDoesNotCallService() throws Exception {
        // validate 与 upload 一样只接受名为 file 的 part；绑定失败时 Service 必须保持零调用。
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("other", new NamedByteArrayResource(FILE_BYTES, "contract-input.bin"));
        ResponseEntity<String> response = postMultipart(body, "/api/v1/config/imports/" + TASK_NO + "/validate");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("success").asBoolean()).isFalse();
        assertThat(json.path("code").asText()).isEqualTo("A9999");
        verifyNoMoreInteractions(service);
    }

    private ResponseEntity<String> postMultipart(String path) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new NamedByteArrayResource(FILE_BYTES, "contract-input.bin"));
        return postMultipart(body, path);
    }

    private ResponseEntity<String> postMultipart(MultiValueMap<String, Object> body, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", "contract-user");
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<MultiValueMap<String, Object>>(body, headers),
                String.class);
    }

    private void assertSuccessEnvelope(ResponseEntity<String> response, String status) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(response.getBody());
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.path("code").asText()).isEqualTo("OK");
        assertThat(json.path("message").asText()).isEqualTo("成功");
        assertThat(json.path("data").path("status").asText()).isEqualTo(status);
    }

    private ConfigurationImportTask task(ImportStatus status) {
        ConfigurationImportTask task = new ConfigurationImportTask(
                1L, TASK_NO, ConfigurationImportType.FX_RATE, "contract-input.bin", null,
                "contract-user", LocalDateTime.of(2026, 10, 20, 12, 0));
        task.moveTo(status);
        return task;
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[32];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    /** 用原始字节和显式文件名构造 multipart 资源，避免测试绑定到 Excel、CSV 等具体文件格式。 */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
