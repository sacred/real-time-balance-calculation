package com.sacred.balance.model;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private boolean success;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(int code, boolean success, String message) {
        this();
        this.code = code;
        this.success = success;
        this.message = message;
    }

    public ApiResponse(int code, boolean success, String message, T data) {
        this(code, success, message);
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, true, "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, true, message, data);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(200, true, message);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, false, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(500, false, message);
    }

}
