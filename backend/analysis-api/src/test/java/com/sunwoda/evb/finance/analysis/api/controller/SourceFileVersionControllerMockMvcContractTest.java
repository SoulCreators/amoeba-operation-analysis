package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.SourceFileVersionService;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileValidationStatus;
import com.sunwoda.evb.finance.analysis.domain.model.SourceFileVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 源文件版本治理接口的 MockMvc 契约：登记、生效切换与版本列表。 */
class SourceFileVersionControllerMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-SFV-001";
    private static final String FILE_ID = "SFV-FILE-001";
    private static final String USER_ID = "finance-user";

    private SourceFileVersionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SourceFileVersionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SourceFileVersionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void registerReturnsVersionAndUsesPrincipalAsUploader() throws Exception {
        SourceFileVersion version = SourceFileVersion.create(FILE_ID, BATCH_NO, "2026-10",
                "DS-SFV", "OBJ-SFV", 1, "TPL-1", "v1", "hash-1", "key-1", 100L, USER_ID);
        when(service.register(BATCH_NO, "DS-SFV", "OBJ-SFV", "TPL-1", "v1", "hash-1",
                "key-1", 100L, null, USER_ID)).thenReturn(version);

        mockMvc.perform(post("/api/v1/batches/{batchNo}/file-versions", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"DS-SFV\",\"objectCode\":\"OBJ-SFV\","
                                + "\"templateCode\":\"TPL-1\",\"templateVersion\":\"v1\","
                                + "\"fileHash\":\"hash-1\",\"objectKey\":\"key-1\",\"rowCount\":100}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.fileId").value(FILE_ID))
                .andExpect(jsonPath("$.data.objectCode").value("OBJ-SFV"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.active").value(false));

        verify(service).register(BATCH_NO, "DS-SFV", "OBJ-SFV", "TPL-1", "v1", "hash-1",
                "key-1", 100L, null, USER_ID);
    }

    @Test
    void activateReturnsActiveVersion() throws Exception {
        SourceFileVersion active = SourceFileVersion.restore(9L, FILE_ID, BATCH_NO, "2026-10",
                "DS-SFV", "OBJ-SFV", 1, "TPL-1", "v1", "hash-1", "key-1", 100L,
                SourceFileValidationStatus.VALID, true, USER_ID, LocalDateTime.now());
        when(service.activate(FILE_ID)).thenReturn(active);

        mockMvc.perform(post("/api/v1/file-versions/{fileId}/activate", FILE_ID)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(FILE_ID))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.validationStatus").value("VALID"));

        verify(service).activate(FILE_ID);
    }

    @Test
    void listReturnsVersionHistory() throws Exception {
        SourceFileVersion v = SourceFileVersion.create(FILE_ID, BATCH_NO, "2026-10",
                "DS-SFV", "OBJ-SFV", 1, "TPL-1", "v1", "hash-1", "key-1", 100L, USER_ID);
        when(service.listVersions("2026-10", "OBJ-SFV")).thenReturn(Collections.singletonList(v));

        mockMvc.perform(get("/api/v1/file-versions")
                        .param("period", "2026-10").param("objectCode", "OBJ-SFV")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileId").value(FILE_ID))
                .andExpect(jsonPath("$.data[0].versionNo").value(1));

        verify(service).listVersions("2026-10", "OBJ-SFV");
    }

    @Test
    void publishedBatchWithoutReasonMapsToA0002Envelope() throws Exception {
        when(service.register(BATCH_NO, "DS-SFV", "OBJ-SFV", "TPL-1", "v1", "hash-1",
                "key-1", 100L, null, USER_ID))
                .thenThrow(new IllegalStateException("批次已发布，重传需提供重算申请原因"));

        mockMvc.perform(post("/api/v1/batches/{batchNo}/file-versions", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"DS-SFV\",\"objectCode\":\"OBJ-SFV\","
                                + "\"templateCode\":\"TPL-1\",\"templateVersion\":\"v1\","
                                + "\"fileHash\":\"hash-1\",\"objectKey\":\"key-1\",\"rowCount\":100}")
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("批次已发布，重传需提供重算申请原因"));
    }

    @Test
    void anonymousPrincipalReturnsUnauthorizedWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/batches/{batchNo}/file-versions", BATCH_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetCode\":\"DS-SFV\",\"objectCode\":\"OBJ-SFV\","
                                + "\"templateCode\":\"TPL-1\",\"fileHash\":\"hash-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("A0002"))
                .andExpect(jsonPath("$.message").value("未获取到认证用户"));
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
