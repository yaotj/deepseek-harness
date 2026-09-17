package com.chinasofti.huateng.paysign.exception;

/** 解约流程专用运行时异常，用于触发 @Transactional 回滚。 */
public class TerminationException extends RuntimeException {

    public TerminationException(String message) {
        super(message);
    }

    public TerminationException(String message, Throwable cause) {
        super(message, cause);
    }
}
