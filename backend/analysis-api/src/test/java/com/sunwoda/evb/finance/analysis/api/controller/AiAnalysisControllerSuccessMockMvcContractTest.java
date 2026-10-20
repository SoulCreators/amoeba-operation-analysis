package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.AiAnalysisService;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraft;
import com.sunwoda.evb.finance.analysis.domain.model.AiAnalysisDraftStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI 分析三个成功入口的 HTTP 契约测试，固定返回包络、草稿字段序列化及参数透传行为。 */
class AiAnalysisControllerSuccessMockMvcContractTest {
    private static final String BATCH_NO = "BATCH-2026-10";
    private static final String USER_ID = "finance-user";
    private static final LocalDateTime GENERATED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 10, 20, 10, 30);

    private AiAnalysisService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiAnalysisService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiAnalysisController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void generateReturnsSuccessfulEnvelopeSerializesDraftAndPassesBatchAndPrincipal() throws Exception {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.DRAFT, null, null);
        when(service.generate(BATCH_NO, USER_ID)).thenReturn(draft);

        // 本次新增契约验证 AI 生成入口的成功包络、草稿字段序列化及路径参数和认证用户透传。
        expectDraftFields(mockMvc.perform(post("/api/v1/ai/analysis/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功")), draft);

        // 本次新增契约确认 Controller 将 batchNo 和 Principal 用户原样传给生成 Service。
        verify(service).generate(BATCH_NO, USER_ID);
    }

    @Test
    void getReturnsSuccessfulEnvelopeSerializesDraftAndPassesBatchAndPrincipal() throws Exception {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.DRAFT, null, null);
        when(service.get(BATCH_NO, USER_ID)).thenReturn(draft);

        // 本次新增契约验证 AI 查询入口对认证用户返回完整草稿对象和统一成功包络。
        expectDraftFields(mockMvc.perform(get("/api/v1/ai/analysis/{batchNo}", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功")), draft);

        // 本次新增契约确认 Controller 将 batchNo 和 Principal 用户原样传给查询 Service。
        verify(service).get(BATCH_NO, USER_ID);
    }

    @Test
    void publishReturnsSuccessfulEnvelopeSerializesPublishedDraftAndPassesBatchAndPrincipal() throws Exception {
        AiAnalysisDraft draft = draft(AiAnalysisDraftStatus.PUBLISHED, USER_ID, PUBLISHED_AT);
        when(service.publish(BATCH_NO, USER_ID)).thenReturn(draft);

        // 本次新增契约验证 AI 发布入口的成功包络、已发布草稿字段及认证用户透传。
        expectDraftFields(mockMvc.perform(post("/api/v1/ai/analysis/{batchNo}/publish", BATCH_NO)
                        .principal(principal(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("成功")), draft);

        // 本次新增契约确认 Controller 将 batchNo 和 Principal 用户原样传给发布 Service。
        verify(service).publish(BATCH_NO, USER_ID);
    }

    private static AiAnalysisDraft draft(AiAnalysisDraftStatus status,
                                         String publishedBy,
                                         LocalDateTime publishedAt) {
        Map<String, String> sections = new LinkedHashMap<String, String>();
        sections.put("summary", "经营结果稳定");
        sections.put("risk", "关注毛利率波动");
        return new AiAnalysisDraft(BATCH_NO, "AI-V1", sections, USER_ID,
                GENERATED_AT, status, publishedBy, publishedAt);
    }

    private static Principal principal(String name) {
        return () -> name;
    }

    private static ResultActions expectDraftFields(ResultActions result, AiAnalysisDraft draft)
            throws Exception {
        // 本次新增契约集中断言 AiAnalysisDraft 的全部可序列化字段，避免遗漏已发布字段。
        ResultActions assertions = result
                .andExpect(jsonPath("$.data.batchNo").value(draft.getBatchNo()))
                .andExpect(jsonPath("$.data.versionNo").value(draft.getVersionNo()))
                .andExpect(jsonPath("$.data.sections.summary").value("经营结果稳定"))
                .andExpect(jsonPath("$.data.sections.risk").value("关注毛利率波动"))
                .andExpect(jsonPath("$.data.generatedBy").value(draft.getGeneratedBy()))
                .andExpect(jsonPath("$.data.generatedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.generatedAt[1]").value(10))
                .andExpect(jsonPath("$.data.generatedAt[2]").value(20))
                .andExpect(jsonPath("$.data.generatedAt[3]").value(10))
                .andExpect(jsonPath("$.data.generatedAt[4]").value(0))
                .andExpect(jsonPath("$.data.status").value(draft.getStatus().name()))
                ;
        if (draft.getPublishedBy() == null) {
            assertions = assertions.andExpect(jsonPath("$.data.publishedBy").value(nullValue()));
        } else {
            assertions = assertions.andExpect(jsonPath("$.data.publishedBy").value(draft.getPublishedBy()));
        }
        if (draft.getPublishedAt() == null) {
            return assertions.andExpect(jsonPath("$.data.publishedAt").value(nullValue()));
        }
        // 本次新增契约锁定当前 LocalDateTime 数组序列化形态及已发布时间字段。
        return assertions
                .andExpect(jsonPath("$.data.publishedAt[0]").value(2026))
                .andExpect(jsonPath("$.data.publishedAt[1]").value(10))
                .andExpect(jsonPath("$.data.publishedAt[2]").value(20))
                .andExpect(jsonPath("$.data.publishedAt[3]").value(10))
                .andExpect(jsonPath("$.data.publishedAt[4]").value(30));
    }
}
