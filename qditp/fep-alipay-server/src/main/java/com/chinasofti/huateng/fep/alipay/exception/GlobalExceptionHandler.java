package com.chinasofti.huateng.fep.alipay.exception;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，统一处理 BusinessException。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public AlipayCommonResponse handleBusinessException(BusinessException e) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(e.getCode());
        response.setRetMsg(e.getMsg());
        return response;
    }

    @ExceptionHandler(Exception.class)
    public AlipayCommonResponse handleException(Exception e) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
        response.setRetMsg("系统内部错误");
        return response;
    }
}
