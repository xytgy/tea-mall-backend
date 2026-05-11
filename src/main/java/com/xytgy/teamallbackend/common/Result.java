package com.xytgy.teamallbackend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一结果封装类
 * @param <T>
 */
@Data
@Schema(description = "统一响应结果封装")
public class Result<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Boolean success;

    private Integer code;

    private String message;

    private T data;

    public static <T> Result<T> success(String message, T data) {
        return build(true, ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> success(T data) {
        return build(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success() {
        return build(true, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> success(String message) {
        return build(true, ResultCode.SUCCESS.getCode(), message, null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        return build(false, code, message, null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return build(false, resultCode.getCode(), resultCode.getMessage(), null);
    }

    private static <T> Result<T> build(Boolean success, Integer code, String message, T data) {
        Result<T> result = new Result<>();
        result.success = success;
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

}
