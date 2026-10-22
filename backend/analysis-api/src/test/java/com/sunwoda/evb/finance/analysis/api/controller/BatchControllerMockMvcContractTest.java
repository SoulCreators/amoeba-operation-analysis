package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.BatchService;
import com.sunwoda.evb.finance.analysis.application.service.PermissionService;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisBatch;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.BatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc contract coverage for BATCH-API-001. */
class BatchControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-001";
    private static final String USER_ID = "U001";

    private BatchService batchService;
    private PermissionService permissionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        batchService = mock(BatchService.class);
        permissionService = mock(PermissionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BatchController(batchService, permissionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void authenticatedCreateReturnsSuccessfulEnvelopePassesPrincipalAndConvertsBasePerspective() throws Exception {
        AnalysisBatch batch = batch(BatchStatus.DRAFT);
        when(permissionService.canCalculate(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(batchService.create(BATCH_NO, "2026-09", AnalysisPerspective.BASE, USER_ID))
                .thenReturn(batch);

        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\",\"period\":\"2026-09\","
                                + "\"perspective\":\"BASE\",\"createdBy\":\"request-user\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.createdBy").value(USER_ID));

        verify(batchService).create(BATCH_NO, "2026-09", AnalysisPerspective.BASE, USER_ID);
    }

    @Test
    void authenticatedGetReturnsSuccessfulEnvelope() throws Exception {
        AnalysisBatch batch = batch(BatchStatus.READY_FOR_CALCULATION);
        when(batchService.get(BATCH_NO)).thenReturn(batch);
        when(permissionService.viewableScopes(USER_ID, AnalysisPerspective.BASE))
                .thenReturn(java.util.Collections.singleton("NORTH"));

        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.batchNo").value(BATCH_NO))
                .andExpect(jsonPath("$.data.perspective").value("BASE"))
                .andExpect(jsonPath("$.data.status").value("READY_FOR_CALCULATION"));

        verify(batchService).get(BATCH_NO);
    }

    @Test
    void authorizedPreparationTransitionReachesBatchService() throws Exception {
        AnalysisBatch batch = batch(BatchStatus.CHECKING);
        when(batchService.get(BATCH_NO)).thenReturn(batch);
        when(permissionService.canCalculate(USER_ID, AnalysisPerspective.BASE)).thenReturn(true);
        when(batchService.move(BATCH_NO, BatchStatus.CHECKING)).thenReturn(batch);

        mockMvc.perform(post("/api/v1/batches/{batchNo}/status", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CHECKING\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHECKING"));

        verify(batchService).move(BATCH_NO, BatchStatus.CHECKING);
    }

    @Test
    void batchCreationRequiresFinanceActionPermission() throws Exception {
        mockMvc.perform(post("/api/v1/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchNo\":\"BATCH-001\",\"period\":\"2026-09\",\"perspective\":\"BASE\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isForbidden());

        verify(batchService, never()).create(BATCH_NO, "2026-09", AnalysisPerspective.BASE, USER_ID);
    }

    @Test
    void genericStatusMutationIsForbiddenAndDoesNotReachBatchService() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchNo}/status", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CALCULATING\"}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isForbidden());

        verify(batchService, never()).move(BATCH_NO, BatchStatus.CALCULATING);
    }

    @Test
    void batchNumberDoesNotGrantReadAccessWithoutPerspectiveScope() throws Exception {
        when(batchService.get(BATCH_NO)).thenReturn(batch(BatchStatus.DRAFT));
        when(permissionService.viewableScopes(USER_ID, AnalysisPerspective.BASE))
                .thenReturn(java.util.Collections.<String>emptySet());

        mockMvc.perform(get("/api/v1/batches/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isForbidden());
    }

    private AnalysisBatch batch(BatchStatus status) {
        return new AnalysisBatch(1L, BATCH_NO, "2026-09", AnalysisPerspective.BASE,
                status, USER_ID, LocalDateTime.of(2026, 10, 20, 10, 0));
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
