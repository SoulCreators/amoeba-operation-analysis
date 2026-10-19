package com.sunwoda.evb.finance.analysis.application.service;

import com.sunwoda.evb.finance.analysis.domain.model.AnalysisPerspective;
import com.sunwoda.evb.finance.analysis.domain.model.UserScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserScopeTest {
    @Test
    void trimsIdentityFieldsAndPreservesDetailExportAndConfigFlags() {
        UserScope scope = new UserScope(" U001 ", " BASE_FINANCE ", AnalysisPerspective.BASE,
                " NANCHANG ", true, false, true, true);

        assertEquals("U001", scope.getUserId());
        assertEquals("BASE_FINANCE", scope.getRoleCode());
        assertEquals("NANCHANG", scope.getScopeCode());
        assertTrue(scope.isCanViewDetail());
        assertFalse(scope.isCanExport());
        assertTrue(scope.isCanEditConfig());
        assertTrue(scope.isEnabled());
    }

    @Test
    void rejectsMissingScopeIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new UserScope(
                " ", "ROLE", AnalysisPerspective.BASE, "SCOPE", false, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new UserScope(
                "U001", " ", AnalysisPerspective.BASE, "SCOPE", false, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new UserScope(
                "U001", "ROLE", null, "SCOPE", false, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new UserScope(
                "U001", "ROLE", AnalysisPerspective.BASE, " ", false, false, false, true));
    }
}
