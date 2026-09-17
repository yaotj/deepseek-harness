package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.exception.PayGatewayException;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 支付签约全局异常处理器。 */
@RestControllerAdvice
public class PaySignExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaySignExceptionHandler.class);

    /** 支付网关调用异常。 */
    @ExceptionHandler(PayGatewayException.class)
    public BaseRespDTO handlePayGatewayException(PayGatewayException e) {
        log.error("调用支付网关失败", e);
        BaseRespDTO response = new BaseRespDTO();
        response.setCode(-1);
        response.setMsg("支付网关调用失败: " + e.getMessage());
        response.setSuccess(Boolean.FALSE);
        response.setRetCode("-1");
        response.setRetMsg("支付网关调用失败");
        return response;
    }

    /** 解约流程异常。触发事务回滚后，向调用方（含支付平台回调）返回 9999，便于重试。 */
    @ExceptionHandler(TerminationException.class)
    public BaseRespDTO handleTerminationException(TerminationException e) {
        log.error("解约流程异常", e);
        BaseRespDTO response = new BaseRespDTO();
        response.setCode(-1);
        response.setMsg(PaySignErrorCodeEnum.FAIL.getMsg());
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(PaySignErrorCodeEnum.FAIL.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.FAIL.getMsg());
        return response;
    }
}
