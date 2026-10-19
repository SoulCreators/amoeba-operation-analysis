package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.application.service.TraceQueryServiceImpl;
import com.sunwoda.evb.finance.analysis.common.ApiResult;
import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TraceQueryControllerTest {
    private TraceQueryServiceImpl traceQueryService;
    private TraceQueryController controller;

    @BeforeEach
    void setUp() {
        traceQueryService = mock(TraceQueryServiceImpl.class);
        controller = new TraceQueryController(traceQueryService);
    }

    @Test
    void rejectsTraceQueryWhenPrincipalIsMissing() {
        assertThrows(IllegalStateException.class,
                () -> controller.trace("BATCH-001", "BASE", "TASK-001", null));

        verifyNoInteractions(traceQueryService);
    }

    @Test
    void usesIdentityFromPrincipalWhenQuerySucceeds() {
        Principal principal = () -> "  principal-user  ";
        List<PnlFactSource> sources = Collections.singletonList(
                new PnlFactSource("TASK-001", "source.xlsx", "checksum",
                        "Sheet1", 2, "NANCHANG", "BASE-001", "PRODUCT-001", "LINE-001"));
        when(traceQueryService.findForUser("BATCH-001", AnalysisPerspective.BASE,
                "TASK-001", "principal-user")).thenReturn(sources);

        ApiResult<List<PnlFactSource>> result = controller.trace(
                "BATCH-001", "BASE", "TASK-001", principal);

        assertTrue(result.isSuccess());
        assertEquals("OK", result.getCode());
        assertSame(sources, result.getData());
        verify(traceQueryService).findForUser(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001", "principal-user");
    }
}
