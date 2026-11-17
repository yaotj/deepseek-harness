package com.chinasofti.huateng.paysign.exception;

public class PayGatewayException extends RuntimeException {

    public PayGatewayException(String message) {
        super(message);
    }

    public PayGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
