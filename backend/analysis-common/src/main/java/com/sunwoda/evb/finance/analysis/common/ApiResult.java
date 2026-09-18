package com.sunwoda.evb.finance.analysis.common;

/**
 * 统一API返回结构，先在一期骨架中固定协议，后续接入公司网关时可复用。
 */
public class ApiResult<T> {
    private boolean success;
    private T data;
    private String code;
    private String message;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.success = true;
        result.data = data;
        result.code = "OK";
        result.message = "成功";
        return result;
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        ApiResult<T> result = new ApiResult<T>();
        result.success = false;
        result.code = code;
        result.message = message;
        return result;
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
