package com.chinasofti.huateng.common.response;

import java.io.Serializable;

public class ResultVO<T> implements Serializable {
    private static final long serialVersionUID = -5616675572470793595L;

    public static final String SUCCESS_CODE = "200";
    public static final String SUCCESS_MSG = "SUCCESS";
    public static final String ERROR_CODE = "500";
    public static final String ERROR_MSG = "内部异常";
    public static final String ILLEGAL_PARAMS_CODE = "400";
    public static final String ILLEGAL_PARAMS_MSG = "非法参数";
    public static final String SIGN_ERROR_CODE = "401";
    public static final String SIGN_ERROR_MSG = "签名异常";
    public static final String CIRCUIT_BREAKER_CODE = "402";
    public static final String CIRCUIT_BREAKER_MSG = "断路器错误";
    public static final String HSM_UNAVAILABLE_CODE = "503";
    public static final String HSM_UNAVAILABLE_MSG = "加密机连接不可用";

    private String code;
    private String msg;
    private T data;

    public ResultVO() {
        this(SUCCESS_CODE, SUCCESS_MSG);
    }

    public ResultVO(String code, String msg) {
        this(code, msg, null);
    }

    public ResultVO(String code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
