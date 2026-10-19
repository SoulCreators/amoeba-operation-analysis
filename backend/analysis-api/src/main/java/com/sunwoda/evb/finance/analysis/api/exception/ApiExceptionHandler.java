package com.sunwoda.evb.finance.analysis.api.exception;

import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResult.fail("A0001", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage();
        HttpStatus status = isUnauthenticated(message)
                ? HttpStatus.UNAUTHORIZED
                : isAuthorizationDenied(message) ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResult.fail("A0002", message));
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception ex) {
        return ApiResult.fail("A9999", "系统处理失败");
    }

    private boolean isUnauthenticated(String message) {
        return message != null && message.startsWith("未获取到认证用户");
    }

    private boolean isAuthorizationDenied(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("权限")
                || message.contains("未授权")
                || message.contains("仅财经角色可查看草稿");
    }
}
