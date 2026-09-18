package com.sunwoda.evb.finance.analysis.api.exception;

import com.sunwoda.evb.finance.analysis.common.ApiResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgument(IllegalArgumentException ex) {
        return ApiResult.fail("A0001", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResult<Void> handleIllegalState(IllegalStateException ex) {
        return ApiResult.fail("A0002", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleOther(Exception ex) {
        return ApiResult.fail("A9999", "系统处理失败");
    }
}
