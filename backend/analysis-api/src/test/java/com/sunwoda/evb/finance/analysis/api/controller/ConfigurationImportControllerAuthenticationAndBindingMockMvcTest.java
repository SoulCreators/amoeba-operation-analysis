package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.exception.ApiExceptionHandler;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MockMvc regression coverage for IMPORT-API-005. */
class ConfigurationImportControllerAuthenticationAndBindingMockMvcTest {
    private static final String TASK_NO = "CFG-TASK-005";

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
    void createRejectsMissingOrBlankPrincipalWithUnauthorizedA0002() throws Exception {
        assertUnauthenticated(createRequest(null));
        assertUnauthenticated(createRequest(principal("")));
        assertUnauthenticated(createRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void getRejectsMissingOrBlankPrincipalWithUnauthorizedA0002() throws Exception {
        assertUnauthenticated(getRequest(null));
        assertUnauthenticated(getRequest(principal("")));
        assertUnauthenticated(getRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void issuesRejectsMissingOrBlankPrincipalWithUnauthorizedA0002() throws Exception {
        assertUnauthenticated(issuesRequest(null));
        assertUnauthenticated(issuesRequest(principal("")));
        assertUnauthenticated(issuesRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void statusRejectsMissingOrBlankPrincipalWithUnauthorizedA0002() throws Exception {
        assertUnauthenticated(statusRequest(null));
        assertUnauthenticated(statusRequest(principal("")));
        assertUnauthenticated(statusRequest(principal("   ")));

        verifyNoInteractions(service);
    }

    @Test
    void createMissingTypeKeepsCurrentTypeConversionPriorityBeforeAuthentication() throws Exception {
        mockMvc.perform(createRequest(null, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void statusMissingStatusKeepsCurrentAuthenticationPriority() throws Exception {
        mockMvc.perform(statusRequest(null, "{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));

        verifyNoInteractions(service);
    }

    @Test
    void createWithMissingTypeUsesCurrentGenericErrorEnvelope() throws Exception {
        mockMvc.perform(createRequest(principal("U005"), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    @Test
    void statusWithMissingStatusUsesCurrentGenericErrorEnvelope() throws Exception {
        mockMvc.perform(statusRequest(principal("U005"), "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A9999"))
                .andExpect(jsonPath("$.message").value("系统处理失败"));

        verifyNoInteractions(service);
    }

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.code").value("A0002"));
    }

    private MockHttpServletRequestBuilder createRequest(Principal principal) {
        return createRequest(principal, "{\"type\":\"FX_RATE\",\"fileName\":\"fx.xlsx\",\"checksum\":\"sha256\"}");
    }

    private MockHttpServletRequestBuilder createRequest(Principal principal, String body) {
        MockHttpServletRequestBuilder request = post("/api/v1/config/imports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return withPrincipal(request, principal);
    }

    private MockHttpServletRequestBuilder getRequest(Principal principal) {
        return withPrincipal(get("/api/v1/config/imports/{taskNo}", TASK_NO), principal);
    }

    private MockHttpServletRequestBuilder issuesRequest(Principal principal) {
        return withPrincipal(get("/api/v1/config/imports/{taskNo}/issues", TASK_NO), principal);
    }

    private MockHttpServletRequestBuilder statusRequest(Principal principal) {
        return statusRequest(principal, "{\"status\":\"VALID\"}");
    }

    private MockHttpServletRequestBuilder statusRequest(Principal principal, String body) {
        MockHttpServletRequestBuilder request = post("/api/v1/config/imports/{taskNo}/status", TASK_NO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return withPrincipal(request, principal);
    }

    private MockHttpServletRequestBuilder withPrincipal(MockHttpServletRequestBuilder request,
                                                        Principal principal) {
        return principal == null ? request : request.principal(principal);
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
