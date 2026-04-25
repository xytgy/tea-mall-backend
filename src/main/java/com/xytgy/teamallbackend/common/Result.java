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

    @Schema(description = "请求是否成功（true=成功，false=失败）", example = "true")
    private Boolean success;

    @Schema(description = "状态码", example = "200")
    private Integer code;

    @Schema(description = "错误信息（失败时返回，成功时为null）", example = "操作成功")
    private String message;

    @Schema(description = "响应数据（成功时返回具体数据，失败时为null）")//错误信息
    private T data; //数据

    public static <T> Result<T> success(String message, T data) {
        return build(true, ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> success(T data) {
        return build(true, ResultCode.SUCCESS.getCode(), "操作成功", data);
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
