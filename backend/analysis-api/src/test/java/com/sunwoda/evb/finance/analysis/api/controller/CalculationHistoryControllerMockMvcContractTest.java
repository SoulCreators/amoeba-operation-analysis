package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.port.ManualRevisionIdempotencyPort;
import com.sunwoda.evb.finance.analysis.application.service.ManualCalculationRevisionService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersion;
import com.sunwoda.evb.finance.analysis.domain.model.CalculationVersionStatus;
import com.sunwoda.evb.finance.analysis.domain.repository.CalculationVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DATA-004-TECH-003-REVISION-003 API 契约测试。
 *
 * <p>本次修改测试：覆盖必需构造器注入、认证主体、历史读取权限、人工修订原因、
 * 应用端口幂等重放和冲突边界；不连接 infrastructure，避免把 API 契约误判为真实数据库事务验收。</p>
 */
class CalculationHistoryControllerMockMvcContractTest {
    private static final String USER_ID = "HQ-FINANCE-001";
    private static final String SOURCE_ID = "CALC-V-001";

    private CalculationVersionRepository versionRepository;
    private PermissionService permissionService;
    private ManualCalculationRevisionService revisionService;
    private ManualRevisionIdempotencyPort idempotencyPort;
    private MockMvc mockMvc;
    private CalculationVersion source;
    private CalculationVersion target;

    @BeforeEach
    void setUp() {
        versionRepository = mock(CalculationVersionRepository.class);
        permissionService = mock(PermissionService.class);
        revisionService = mock(ManualCalculationRevisionService.class);
        // 本次修改：用测试专用端口实现验证 Controller 不再持有进程内 requestKey 缓存。
        idempotencyPort = new InMemoryIdempotencyPort();
        source = version(SOURCE_ID, "V001", false);
        target = version("CALC-MR-001", "MR-001", true);
        when(versionRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(permissionService.canManualRevise(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(permissionService.canViewHistory(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(revisionService.revise(SOURCE_ID, USER_ID, "修正毛利分摊")).thenReturn(target);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculationHistoryController(
                        versionRepository, permissionService, revisionService, idempotencyPort))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authorizedHistoryQueryUsesPrincipalAndPerspective() throws Exception {
        when(versionRepository.findByBatchNo("BATCH-001", AnalysisPerspective.BASE))
                .thenReturn(Arrays.asList(source, target));

        mockMvc.perform(get("/api/v1/calculations/BATCH-001/versions")
                        .param("perspective", "BASE")
                        .principal(() -> USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].calculationVersionId").value(SOURCE_ID))
                .andExpect(jsonPath("$.data[1].calculationVersionId").value("CALC-MR-001"));

        verify(permissionService).canViewHistory(USER_ID, AnalysisPerspective.BASE);
        verify(versionRepository).findByBatchNo("BATCH-001", AnalysisPerspective.BASE);
    }

    @Test
    void historyQueryRejectsUserWithoutHistoryPermission() throws Exception {
        when(permissionService.canViewHistory(USER_ID, AnalysisPerspective.BASE)).thenReturn(false);

        mockMvc.perform(get("/api/v1/calculations/BATCH-001/versions")
                        .param("perspective", "BASE")
                        .principal(() -> USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"));

        verify(versionRepository, never()).findByBatchNo("BATCH-001", AnalysisPerspective.BASE);
    }

    @Test
    void revisionUsesAuthenticatedPrincipalAndRequiresReason() throws Exception {
        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceVersionId\":\"CALC-V-001\","
                                + "\"revisionReason\":\"修正毛利分摊\","
                                + "\"requestKey\":\"REQ-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.calculationVersionId").value("CALC-MR-001"));

        verify(revisionService).revise(SOURCE_ID, USER_ID, "修正毛利分摊");
    }

    @Test
    void missingIdempotencyPortIsRejectedByRequiredConstructor() {
        // 本次新增：没有持久幂等端口时直接拒绝构造，禁止回退到内存缓存或空 Bean。
        assertThrows(IllegalArgumentException.class, () -> new CalculationHistoryController(
                versionRepository, permissionService, revisionService, null));
    }

    @Test
    void revisionWithoutReasonOrRequestKeyIsRejectedBeforeApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceVersionId\":\"CALC-V-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"))
                .andExpect(jsonPath("$.message").value("revisionReason不能为空"));

        verifyNoInteractions(versionRepository, permissionService, revisionService);
    }

    @Test
    void sameRequestKeyAndPayloadReplaysWithoutCreatingAnotherVersion() throws Exception {
        String body = "{\"sourceVersionId\":\"CALC-V-001\","
                + "\"revisionReason\":\"修正毛利分摊\",\"requestKey\":\"REQ-001\"}";

        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> USER_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> USER_ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calculationVersionId").value("CALC-MR-001"));

        verify(revisionService).revise(SOURCE_ID, USER_ID, "修正毛利分摊");
    }

    @Test
    void sameRequestKeyWithDifferentPayloadIsRejected() throws Exception {
        sameRequestKeyAndPayloadReplaysWithoutCreatingAnotherVersion();

        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceVersionId\":\"CALC-V-001\","
                                + "\"revisionReason\":\"改成其他原因\",\"requestKey\":\"REQ-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0001"));
    }

    @Test
    void anonymousRevisionCannotReachApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceVersionId\":\"CALC-V-001\","
                                + "\"revisionReason\":\"修正毛利分摊\",\"requestKey\":\"REQ-001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verifyNoInteractions(versionRepository, permissionService, revisionService);
    }

    @Test
    void adminReadPermissionDoesNotBecomeRevisionWritePermission() throws Exception {
        when(permissionService.canManualRevise("ADMIN-001", AnalysisPerspective.BASE)).thenReturn(false);

        mockMvc.perform(post("/api/v1/calculations/revisions")
                        .principal(() -> "ADMIN-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceVersionId\":\"CALC-V-001\","
                                + "\"revisionReason\":\"修正毛利分摊\",\"requestKey\":\"REQ-ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"));

        verify(revisionService, never()).revise(SOURCE_ID, "ADMIN-001", "修正毛利分摊");
    }

    private CalculationVersion version(String id, String versionNo, boolean current) {
        return new CalculationVersion(id, "BATCH-001", AnalysisPerspective.BASE, versionNo,
                "JOB-001", "SNAPSHOT-001", "SOURCE-HASH", "CONFIG-HASH", "INPUT-FINGERPRINT",
                CalculationVersionStatus.CALCULATED, current, null, "TRACE-001", USER_ID,
                LocalDateTime.of(2026, 10, 21, 10, 0), null, null, null, null);
    }

    /** 本次新增：只在契约测试中模拟持久端口的原子重放语义，生产代码不使用该实现。 */
    private static final class InMemoryIdempotencyPort implements ManualRevisionIdempotencyPort {
        private final Map<String, RequestRecord> records = new LinkedHashMap<String, RequestRecord>();

        @Override
        public synchronized CalculationVersion execute(String userId, String requestKey,
                                                        String sourceVersionId, String revisionReason,
                                                        RevisionOperation operation) {
            String key = userId + "\u0000" + requestKey;
            RequestRecord existing = records.get(key);
            if (existing != null) {
                if (!existing.matches(sourceVersionId, revisionReason)) {
                    throw new IllegalArgumentException("相同 requestKey 不允许提交不同人工修订内容");
                }
                return existing.result;
            }
            CalculationVersion result = operation.execute();
            records.put(key, new RequestRecord(sourceVersionId, revisionReason, result));
            return result;
        }
    }

    private static final class RequestRecord {
        private final String sourceVersionId;
        private final String revisionReason;
        private final CalculationVersion result;

        private RequestRecord(String sourceVersionId, String revisionReason, CalculationVersion result) {
            this.sourceVersionId = sourceVersionId;
            this.revisionReason = revisionReason;
            this.result = result;
        }

        private boolean matches(String requestedSourceVersionId, String requestedReason) {
            return sourceVersionId.equals(requestedSourceVersionId)
                    && revisionReason.equals(requestedReason);
        }
    }
}
