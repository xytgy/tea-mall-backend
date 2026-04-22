package com.xytgy.teamallbackend.exception;


import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = ServiceException.class)
    public Result<?> handleServiceException(ServiceException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public Result<?> handleException(Exception e) {
        e.printStackTrace();
        return Result.error(ResultCode.ERROR);
    }
}
