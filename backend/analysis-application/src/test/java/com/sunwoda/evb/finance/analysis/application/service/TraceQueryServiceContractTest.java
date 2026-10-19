package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.PnlFactSource;
import com.sunwoda.evb.finance.analysis.application.port.PnlFactTraceQuery;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authorization and parameter contract for the trace query application role.
 *
 * <p>The expected application port is
 * {@code PnlFactTraceQuery.findForUser(batchNo, perspective, taskNo, userId)}.
 * The application service must validate identity and query parameters, resolve
 * detail scopes before touching the source repository, and filter returned rows
 * to the caller's scope.</p>
 */
class TraceQueryServiceContractTest {
    @Test
    void rejectsTraceQueryWithoutIdentity() throws Exception {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.findForUser("BATCH-001", AnalysisPerspective.BASE,
                        "TASK-001", null));

        verify(fixture.query, never()).findForUser(any(String.class), any(AnalysisPerspective.class),
                any(String.class), any(String.class));
    }

    @Test
    void rejectsTraceQueryWithoutDetailPermission() throws Exception {
        Fixture fixture = fixture();

        assertThrows(IllegalStateException.class,
                () -> fixture.service.findForUser("BATCH-001", AnalysisPerspective.BASE,
                        "TASK-001", "U-NO-DETAIL"));

        verify(fixture.query, never()).findForUser(any(String.class), any(AnalysisPerspective.class),
                any(String.class), any(String.class));
    }

    @Test
    void rejectsTraceQueryOutsideAuthorizedScope() throws Exception {
        Fixture fixture = fixture();

        List<PnlFactSource> rows = fixture.service.findForUser(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001", "U-CROSS-SCOPE");

        assertEquals(Collections.emptyList(), rows);
    }

    @Test
    void returnsRowsForAuthorizedScope() throws Exception {
        Fixture fixture = fixture();

        List<PnlFactSource> rows = fixture.service.findForUser(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001", "U001");

        assertEquals(1, rows.size());
        assertEquals("NANCHANG", rows.get(0).getScopeCode());
    }

    @Test
    void returnsRowsForAllScopeGrant() throws Exception {
        Fixture fixture = fixture();

        List<PnlFactSource> rows = fixture.service.findForUser(
                "BATCH-001", AnalysisPerspective.BASE, "TASK-001", "U-ALL");

        assertEquals(2, rows.size());
    }

    private Fixture fixture() {
        PnlFactTraceQuery query = mock(PnlFactTraceQuery.class);
        when(query.findForUser(eq("BATCH-001"), eq(AnalysisPerspective.BASE), eq("TASK-001"),
                any(String.class)))
                .thenReturn(Arrays.asList(
                        source("NANCHANG"), source("YICHANG")));

        PermissionService permissions = mock(PermissionService.class);
        when(permissions.detailScopes("U001", AnalysisPerspective.BASE))
                .thenReturn(Collections.singleton("NANCHANG"));
        when(permissions.detailScopes("U-ALL", AnalysisPerspective.BASE))
                .thenReturn(Collections.singleton("ALL"));
        when(permissions.detailScopes("U-CROSS-SCOPE", AnalysisPerspective.BASE))
                .thenReturn(Collections.singleton("BEIJING"));
        when(permissions.detailScopes("U-NO-DETAIL", AnalysisPerspective.BASE))
                .thenReturn(Collections.<String>emptySet());

        return new Fixture(new TraceQueryServiceImpl(permissions, query), query);
    }

    private PnlFactSource source(String scopeCode) {
        return new PnlFactSource("TASK-001", "source.xlsx", "checksum",
                "Sheet1", "NANCHANG".equals(scopeCode) ? 2 : 3,
                scopeCode, "BASE-001", "PRODUCT-001", "LINE-001");
    }

    private static final class Fixture {
        private final TraceQueryServiceImpl service;
        private final PnlFactTraceQuery query;

        private Fixture(TraceQueryServiceImpl service, PnlFactTraceQuery query) {
            this.service = service;
            this.query = query;
        }
    }
}
