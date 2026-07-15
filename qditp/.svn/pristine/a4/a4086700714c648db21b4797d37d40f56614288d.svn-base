package com.chinasofti.huateng.acc.security.feign.domain;

import java.io.Serializable;

/**
 * Class description goes here.
 *
 * @version：2018/5/30 10:02
 * @author：ruan
 */
public class BaseResult implements Serializable {

    public static final int CODE_SUCCESS = 0;//成功
    private static final long serialVersionUID = 8954699762398960351L;
    private int ErrorCode;
    private String ErrorMessage;

    public BaseResult() {
        ErrorCode = 0;
        ErrorMessage = "操作成功";
    }

    public BaseResult(int errorCode, String errorMessage) {
        ErrorCode = errorCode;
        ErrorMessage = errorMessage;
    }

    public BaseResult(BaseResult result) {
        ErrorCode = result.getErrorCode();
        ErrorMessage = result.getErrorMessage();
    }

    public static BaseResult hystrix() {
        return new BaseResult(-1, "进入断路器");
    }

    public static BaseResult ofSuccess(String msg) {
        return new BaseResult(CODE_SUCCESS, msg);
    }

    public static BaseResult ofFailure(String msg) {
        return new BaseResult(1, msg);
    }

    public static BaseResult ofSystemException() {
        return new BaseResult(1, "系统异常");
    }

    public int getErrorCode() {
        return ErrorCode;
    }

    public void setErrorCode(int errorCode) {
        ErrorCode = errorCode;
    }

    public String getErrorMessage() {
        return ErrorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        ErrorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "执行结果 {" +
                "ErrorCode=" + ErrorCode +
                ", ErrorMessage='" + ErrorMessage + '\'' +
                '}';
    }
}
