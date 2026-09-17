package com.chinasofti.huateng.alipay.paysign.model.response;

/** 支付中心网关响应封装。 */
public class PayCenterResponse {
    /**
     * 响应码（0-成功，其他-失败）。
     */
    private Integer code;
    /**
     * 通知类接口响应码（0000-成功）。
     */
    private String retCode;
    /**
     * 通知类接口响应描述。
     */
    private String retMsg;
    /**
     * 响应信息。
     */
    private String msg;
    /**
     * 响应数据（业务数据，Base64编码的JSON）。
     */
    private String data;
    /**
     * 是否成功。
     */
    private Boolean success;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
