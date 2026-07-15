package com.chinasofti.huateng.model;

import java.io.Serializable;

public class SimpleResultVo<T> implements Serializable {

    private String code;
    private String msg;
    private T data;

    public SimpleResultVo() {
    }

    public static <T> SimpleResultVo ok() {
        SimpleResultVo simpleResultVo = new SimpleResultVo();
        simpleResultVo.setCode("200");
        return simpleResultVo;
    }

    public static <T> SimpleResultVo ok(T data) {
        SimpleResultVo simpleResultVo = new SimpleResultVo();
        simpleResultVo.setData(data);
        simpleResultVo.setCode("200");
        return simpleResultVo;
    }

    public static <T> SimpleResultVo error(String msg) {
        SimpleResultVo simpleResultVo = new SimpleResultVo();
        simpleResultVo.setMsg(msg);
        return simpleResultVo;
    }

    public static <T> SimpleResultVo error(String msg, String code) {
        SimpleResultVo simpleResultVo = new SimpleResultVo();
        simpleResultVo.setMsg(msg);
        simpleResultVo.setCode(code);
        return simpleResultVo;
    }

    /**
     * 当作为 WebClient 调用的返回对象时，判断参数是否映射了值。
     */
    public boolean checkNotMapping() {
        return code == null && data == null && msg == null;
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
        return "SimpleResultVo{" + "code='" + code + '\'' + ", msg='" + msg + '\'' + ", data=" + data + '}';
    }
}
