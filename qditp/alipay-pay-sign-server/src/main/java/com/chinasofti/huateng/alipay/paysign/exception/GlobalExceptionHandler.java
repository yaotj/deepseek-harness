package com.chinasofti.huateng.alipay.paysign.exception;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将 {@link BusinessException} 统一转换为响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public AlipayCommonResponse handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMsg());
        return e.toResponse();
    }

    @ExceptionHandler(Exception.class)
    public AlipayCommonResponse handleSystemException(Exception e) {
        log.error("系统内部异常", e);
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
        response.setRetMsg("系统内部错误");
        return response;
    }
}
