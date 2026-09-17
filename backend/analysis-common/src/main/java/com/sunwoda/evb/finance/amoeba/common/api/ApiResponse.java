package com.sunwoda.evb.finance.amoeba.common.api;

/** 统一API返回结构。 */
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.success = true;
        response.code = "0";
        response.message = "OK";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        ApiResponse<T> response = new ApiResponse<T>();
        response.success = false;
        response.code = code;
        response.message = message;
        return response;
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
