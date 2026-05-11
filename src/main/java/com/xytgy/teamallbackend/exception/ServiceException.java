package com.xytgy.teamallbackend.exception;

import com.xytgy.teamallbackend.common.ResultCode;
import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {
    private final Integer code;

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public ServiceException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }


}
