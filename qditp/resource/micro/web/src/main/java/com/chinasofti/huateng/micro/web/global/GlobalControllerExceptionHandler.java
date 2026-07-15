package com.chinasofti.huateng.micro.web.global;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;


@RestControllerAdvice
public class GlobalControllerExceptionHandler {

    public static Logger log = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationExceptions(HttpServletRequest request, MethodArgumentNotValidException ex) {
        ApiErrorResponse apiErrorResponse = new ApiErrorResponse(request.getRequestURL().toString(), ex);
        DefaultMessageSourceResolvable defaultMessageSourceResolvable = (DefaultMessageSourceResolvable) ex.getBindingResult().getAllErrors().get(0).getArguments()[0];
        StringBuilder msg = new StringBuilder();
        if (StrUtil.isEmptyIfStr(defaultMessageSourceResolvable.getDefaultMessage())) {
            msg.append("para check,")
                    .append("msg=").append(ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        } else {
            msg.append("check ").append(defaultMessageSourceResolvable.getDefaultMessage())
                    .append(" msg=").append(ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        }
        apiErrorResponse.setMsg(msg.toString());
        return apiErrorResponse;
    }

    @ExceptionHandler(value = {ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse constraintViolationException(HttpServletRequest request, ConstraintViolationException ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }

    @ExceptionHandler(value = {IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse IllegalArgumentException(HttpServletRequest request, IllegalArgumentException ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }

    @ExceptionHandler(value = {NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse noHandlerFoundException(HttpServletRequest request, Exception ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }

    @ExceptionHandler(value = {MethodArgumentConversionNotSupportedException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ApiErrorResponse methodArgumentConversionNotSupportedException(HttpServletRequest request, Exception ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }

    @ExceptionHandler(value = {HttpMediaTypeNotSupportedException.class})
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorResponse httpMediaTypeNotSupportedException(HttpServletRequest request, Exception ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }

    @ExceptionHandler(value = {Exception.class})
    @ResponseStatus(HttpStatus.OK)
    public ApiErrorResponse unknownException(HttpServletRequest request, Exception ex) {
        return new ApiErrorResponse(request.getRequestURL().toString(), ex);
    }
}
