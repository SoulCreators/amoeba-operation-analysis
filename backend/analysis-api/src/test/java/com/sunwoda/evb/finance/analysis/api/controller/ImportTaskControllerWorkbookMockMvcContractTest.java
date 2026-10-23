package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import com.sunwoda.evb.finance.analysis.domain.model.ImportTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 样表整本上传 HTTP 契约测试，固定 multipart 绑定与认证主体传递行为。 */
class ImportTaskControllerWorkbookMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-2026-01";
    private static final String USER_ID = "finance-user";

    private ImportTaskService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ImportTaskService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ImportTaskController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void importWorkbookBindsFileAndUsesPrincipalUser() throws Exception {
        ImportTask act = new ImportTask(1L, "TASK-ACT", BATCH_NO, "PLBU_ACT_INC_COST",
                "样表.xlsx", null, USER_ID, LocalDateTime.of(2026, 10, 20, 10, 0));
        ImportTask bud = new ImportTask(2L, "TASK-BUD", BATCH_NO, "PLBU_BUD_INC_COST",
                "样表.xlsx", null, USER_ID, LocalDateTime.of(2026, 10, 20, 10, 0));
        List<ImportTask> imported = Arrays.asList(act, bud);
        when(service.importWorkbook(eq(BATCH_NO), eq("样表.xlsx"), any(InputStream.class), eq(USER_ID)))
                .thenReturn(imported);

        MockMultipartFile file = new MockMultipartFile("file", "样表.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1});

        mockMvc.perform(multipart("/api/v1/batches/{batchNo}/imports/workbook", BATCH_NO)
                        .file(file)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].datasetCode").value("PLBU_ACT_INC_COST"))
                .andExpect(jsonPath("$.data[1].datasetCode").value("PLBU_BUD_INC_COST"));

        verify(service).importWorkbook(eq(BATCH_NO), eq("样表.xlsx"), any(InputStream.class), eq(USER_ID));
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
