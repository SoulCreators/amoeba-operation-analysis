package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
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
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
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
 * DATA-004-TECH-003-REVISION-001 API 契约测试。
 *
 * <p>本次新增测试：覆盖认证主体、历史读取权限、人工修订原因、requestKey 重放和冲突边界；
 * 不连接 infrastructure，避免把 API 契约误判为真实数据库事务验收。</p>
 */
class CalculationHistoryControllerMockMvcContractTest {
    private static final String USER_ID = "HQ-FINANCE-001";
    private static final String SOURCE_ID = "CALC-V-001";

    private CalculationVersionRepository versionRepository;
    private PermissionService permissionService;
    private ManualCalculationRevisionService revisionService;
    private MockMvc mockMvc;
    private CalculationVersion source;
    private CalculationVersion target;

    @BeforeEach
    void setUp() {
        versionRepository = mock(CalculationVersionRepository.class);
        permissionService = mock(PermissionService.class);
        revisionService = mock(ManualCalculationRevisionService.class);
        source = version(SOURCE_ID, "V001", false);
        target = version("CALC-MR-001", "MR-001", true);
        when(versionRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(permissionService.canManualRevise(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(permissionService.canViewHistory(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(revisionService.revise(SOURCE_ID, USER_ID, "修正毛利分摊")).thenReturn(target);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculationHistoryController(
                        versionRepository, permissionService, revisionService))
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
}
