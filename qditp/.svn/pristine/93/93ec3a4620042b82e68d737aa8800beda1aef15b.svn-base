package com.chinasofti.huateng.micro.web.global;

import java.io.Serializable;

public class ASimpleResultVo<T> implements Serializable {

    public static final String NET_WORK_ERR = "-1";

    private String code;
    private String msg;
    private T data;

    public ASimpleResultVo() {
    }

    /**
     * 当作为 WebClient 调用的返回对象时，判断是否获取了下游目标服务的返回报文。
     */
    public boolean checkNetWorkErr() {
        if (code != null && code.equals(NET_WORK_ERR)) {
            return true;
        }
        return false;
    }

    /**
     * 当作为 WebClient 调用的返回对象时，判断参数是否映射了值。
     */
    public boolean checkNotMapping() {
        if (code == null && data == null && msg == null) {
            return true;
        }
        return false;
    }

    public ASimpleResultVo(String errorCode) {
        this.code = errorCode;
    }

    public ASimpleResultVo(T data) {
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

    @Override
    public String toString() {
        return "ASimpleResultVo{" +
                "code='" + code + '\'' +
                ", msg='" + msg + '\'' +
                ", data=" + data +
                '}';
    }
}
