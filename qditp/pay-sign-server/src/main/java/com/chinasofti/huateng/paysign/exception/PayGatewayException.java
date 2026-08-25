package com.chinasofti.huateng.paysign.exception;

/**
 * 支付网关调用异常。
 */
public class PayGatewayException extends RuntimeException {

    public PayGatewayException(String message) {
        super(message);
    }

    public PayGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
