package com.chinasofti.huateng.paysign.exception;

public class TerminationException extends RuntimeException {

    public TerminationException(String message) {
        super(message);
    }

    public TerminationException(String message, Throwable cause) {
        super(message, cause);
    }
}
