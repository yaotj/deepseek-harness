package com.chinasofti.huateng.collectpay.utils;

import java.io.Serializable;

/** Class description goes here. */
public class BaseResult implements Serializable {

    private static final long serialVersionUID = 8954699762398960351L;
    public final static Integer SUCCESS = 0;
    public final static String SUCCESSMSG = "成功";
    public final static Integer FAILED = 1;
    public final static String FAILEDMSG = "失败";

    private Object data;
    private Integer errorCode;
    private String errorMessage;

    public BaseResult() {
    }

    public BaseResult(Integer errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public BaseResult(Integer errorCode, String errorMessage, Object data) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.data = data;
    }

    public static BaseResult success() {
        return new BaseResult(SUCCESS, SUCCESSMSG);
    }
    public static BaseResult successMsg(String msg) {
        return new BaseResult(SUCCESS, msg);
    }

    public static BaseResult successData(Object data) {
        return new BaseResult(SUCCESS, SUCCESSMSG,data);
    }

    public static BaseResult error() {
        return new BaseResult(FAILED, FAILEDMSG);
    }

    public static BaseResult errorMsg(String errorMessage) {
        return new BaseResult(FAILED, errorMessage);
    }


    public Object getData() {
        return data;
    }

    public void setData(String data) {
        data = data;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

}
