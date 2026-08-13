package com.chinasofti.huateng.fep.alipay.exception;

import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;

/**
 * 业务异常，用于在 Service 层抛出。
 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 响应码 */
    private final String code;

    /** 响应信息 */
    private final String msg;

    public BusinessException(String code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public BusinessException(String code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
        this.msg = msg;
    }

    public BusinessException(String msg) {
        this(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), msg);
    }

    public BusinessException(String msg, Throwable cause) {
        this(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), msg, cause);
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
