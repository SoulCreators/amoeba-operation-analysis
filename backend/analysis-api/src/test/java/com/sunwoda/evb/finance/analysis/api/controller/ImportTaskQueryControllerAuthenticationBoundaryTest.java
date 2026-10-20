package com.sunwoda.evb.finance.analysis.api.controller;

import com.sunwoda.evb.finance.analysis.api.dto.MoveImportTaskRequest;
import com.sunwoda.evb.finance.analysis.application.service.ImportTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ImportTaskQueryControllerAuthenticationBoundaryTest {
    private ImportTaskService importTaskService;
    private ImportTaskQueryController controller;

    @BeforeEach
    void setUp() {
        importTaskService = mock(ImportTaskService.class);
        controller = new ImportTaskQueryController(importTaskService);
    }

    @Test
    void rejectsQueryAndStateOperationsWhenPrincipalIsMissingOrBlank() {
        assertRejected(principal -> controller.get("TASK-001", principal));
        assertRejected(principal -> controller.issues("TASK-001", principal));
        assertRejected(principal -> controller.move("TASK-001", moveRequest(), principal));
        assertRejected(principal -> controller.validate("TASK-001", null, principal));

        verifyNoInteractions(importTaskService);
    }

    private void assertRejected(Invocation invocation) {
        assertRejected(() -> invocation.invoke(null));
        // Principal 存在但名称为 null 时，仍应在认证门禁处拒绝并保持 Service 零调用。
        assertRejected(() -> invocation.invoke(() -> null));
        assertRejected(() -> invocation.invoke(() -> ""));
        assertRejected(() -> invocation.invoke(() -> "   "));
    }

    private void assertRejected(Executable executable) {
        assertThrows(IllegalStateException.class, executable);
    }

    private MoveImportTaskRequest moveRequest() {
        MoveImportTaskRequest request = new MoveImportTaskRequest();
        request.setStatus("VALID");
        return request;
    }

    @FunctionalInterface
    private interface Invocation {
        void invoke(Principal principal);
    }
}
