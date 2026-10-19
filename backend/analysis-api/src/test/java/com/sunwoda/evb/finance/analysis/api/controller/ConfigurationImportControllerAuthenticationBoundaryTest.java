package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.MoveConfigurationImportRequest;
import com.sunwoda.evb.finance.analysis.application.service.ConfigurationImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ConfigurationImportControllerAuthenticationBoundaryTest {
    private ConfigurationImportService service;
    private ConfigurationImportController controller;

    @BeforeEach
    void setUp() {
        service = mock(ConfigurationImportService.class);
        controller = new ConfigurationImportController(service);
    }

    @Test
    void rejectsQueryAndStateOperationsWhenPrincipalIsMissingOrBlank() {
        assertRejected(principal -> controller.get("TASK-001", principal));
        assertRejected(principal -> controller.issues("TASK-001", principal));
        assertRejected(principal -> controller.move("TASK-001", moveRequest(), principal));

        verifyNoInteractions(service);
    }

    private void assertRejected(Invocation invocation) {
        assertRejected(() -> invocation.invoke(null));
        assertRejected(() -> invocation.invoke(() -> ""));
        assertRejected(() -> invocation.invoke(() -> "   "));
    }

    private void assertRejected(Executable executable) {
        assertThrows(IllegalStateException.class, executable);
    }

    private MoveConfigurationImportRequest moveRequest() {
        MoveConfigurationImportRequest request = new MoveConfigurationImportRequest();
        request.setStatus("VALID");
        return request;
    }

    @FunctionalInterface
    private interface Invocation {
        void invoke(Principal principal);
    }
}
